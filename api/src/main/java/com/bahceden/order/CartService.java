package com.bahceden.order;

import com.bahceden.catalog.ProductMapper;
import com.bahceden.catalog.ProductRepository;
import com.bahceden.catalog.ProductService;
import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.order.domain.Cart;
import com.bahceden.order.domain.CartItem;
import com.bahceden.order.dto.CartLine;
import com.bahceden.order.dto.CartSellerGroup;
import com.bahceden.order.dto.CartView;
import com.bahceden.seller.domain.Seller;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hesabın sepeti (cart-and-checkout spec'i, add-cart-and-checkout D4).
 *
 * <p>Sepet yalnızca ürün ve adet tutar; fiyat, stok ve satın alınabilirlik her
 * okumada üründen gelir. Satırlar satıcıya göre gruplanır; kargo ücreti ve
 * toplamlar {@link OrderPricing} ile hesaplanır. Satın alınamaz hale gelen
 * satır sepette kalır ama toplamlara katılmaz.
 *
 * <p>Hesap kimliği her zaman token'dan gelir; sepete yalnızca bu kimlikle
 * ulaşılır (ADR-007).
 */
@Service
public class CartService {

    /** Sepetteki farklı ürün sayısının üst sınırı (D4). */
    static final int MAX_LINES = 50;

    static final String CURRENCY = "TRY";

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final ProductService productService;
    private final ProductMapper productMapper;
    private final OrderPricing pricing;
    private final Clock clock;

    CartService(CartRepository carts, CartItemRepository items, ProductRepository products,
                ProductService productService, ProductMapper productMapper, OrderPricing pricing, Clock clock) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.productService = productService;
        this.productMapper = productMapper;
        this.pricing = pricing;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartView view(long accountId) {
        return toView(lines(accountId));
    }

    /**
     * Satırı ekler veya adedini değiştirir.
     *
     * @throws ApiException {@code PRODUCT_NOT_FOUND}, {@code CANNOT_BUY_OWN_PRODUCT},
     *                      {@code PRODUCT_NOT_PURCHASABLE}, {@code CART_FULL} veya
     *                      {@code quantity} alanına bağlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public CartView setQuantity(long accountId, long productId, int quantity) {
        var product = products.findWithSellerById(productId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "error.productNotFound"));
        if (product.getSeller().getAccountId().filter(owner -> owner == accountId).isPresent()) {
            throw new ApiException(ApiErrorCode.CANNOT_BUY_OWN_PRODUCT, "error.cannotBuyOwnProduct");
        }
        if (!product.isPurchasable()) {
            throw new ApiException(ApiErrorCode.PRODUCT_NOT_PURCHASABLE, "error.productNotPurchasable");
        }
        if (quantity > product.getStockQuantity()) {
            throw ApiException.invalidField("quantity", "validation.cartQuantity.stock");
        }
        var now = clock.instant();
        var cart = carts.findByAccountId(accountId).orElseGet(() -> carts.save(Cart.of(accountId, now)));
        var existing = items.findByCartIdAndProductId(cart.getId(), productId);
        if (existing.isPresent()) {
            existing.get().changeQuantity(quantity, now);
        } else {
            if (items.countByCartId(cart.getId()) >= MAX_LINES) {
                throw new ApiException(ApiErrorCode.CART_FULL, "error.cartFull");
            }
            items.save(CartItem.of(cart.getId(), product, quantity, now));
        }
        cart.touch(now);
        return toView(items.findLinesOf(cart.getId()));
    }

    /** Satırı siler; satır yoksa sepet olduğu gibi döner. */
    @Transactional
    public CartView remove(long accountId, long productId) {
        var cart = carts.findByAccountId(accountId);
        if (cart.isEmpty()) {
            return toView(List.of());
        }
        items.findByCartIdAndProductId(cart.get().getId(), productId).ifPresent(items::delete);
        cart.get().touch(clock.instant());
        return toView(items.findLinesOf(cart.get().getId()));
    }

    /** Sepet satırları, ürün ve satıcıyla; sepet yoksa boş. Sipariş oluşturma da kullanır (D6). */
    List<CartItem> lines(long accountId) {
        return carts.findByAccountId(accountId).map(cart -> items.findLinesOf(cart.getId())).orElse(List.of());
    }

    private CartView toView(List<CartItem> lines) {
        var covers = productService.coverViews(lines.stream().map(line -> line.getProduct().getId()).toList());
        Map<Long, List<CartItem>> bySeller = new LinkedHashMap<>();
        for (var line : lines) {
            bySeller.computeIfAbsent(line.getProduct().getSeller().getId(), id -> new ArrayList<>()).add(line);
        }

        var groups = new ArrayList<CartSellerGroup>();
        long itemsTotal = 0;
        long shippingTotal = 0;
        int itemCount = 0;
        boolean hasUnavailable = false;
        for (var sellerLines : bySeller.values()) {
            Seller seller = sellerLines.getFirst().getProduct().getSeller();
            long groupItems = 0;
            int dispatchDays = 0;
            var views = new ArrayList<CartLine>();
            for (var line : sellerLines) {
                Product product = line.getProduct();
                boolean available = isAvailable(line);
                long lineTotal = product.getPrice().amountInMinorUnits() * line.getQuantity();
                if (available) {
                    groupItems += lineTotal;
                    itemCount += line.getQuantity();
                    dispatchDays = Math.max(dispatchDays, product.getDispatchDays());
                } else {
                    hasUnavailable = true;
                }
                views.add(new CartLine(product.getId(), product.getSlug(), product.getName(), product.getUnit(),
                        MoneyView.from(product.getPrice()), line.getQuantity(), money(lineTotal),
                        product.getStockQuantity(), available, covers.get(product.getId())));
            }
            long shippingFee = pricing.shippingFee(groupItems);
            itemsTotal += groupItems;
            shippingTotal += shippingFee;
            groups.add(new CartSellerGroup(productMapper.toSellerSummary(seller), List.copyOf(views), money(groupItems),
                    money(shippingFee), money(pricing.freeShippingRemaining(groupItems)), dispatchDays));
        }
        return new CartView(List.copyOf(groups), itemCount, money(itemsTotal), money(shippingTotal),
                money(itemsTotal + shippingTotal), hasUnavailable);
    }

    /** Satır siparişe girebilir mi: ürün satın alınabilir ve adet güncel stoğu aşmıyor. */
    static boolean isAvailable(CartItem line) {
        return line.getProduct().isPurchasable() && line.getQuantity() <= line.getProduct().getStockQuantity();
    }

    static MoneyView money(long amount) {
        return new MoneyView(amount, CURRENCY);
    }
}
