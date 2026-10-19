package com.bahceden.order;

import com.bahceden.account.AccountRepository;
import com.bahceden.account.domain.Account;
import com.bahceden.catalog.ProductRepository;
import com.bahceden.catalog.domain.Product;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.time.BusinessDays;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.order.domain.CartItem;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.OrderStatus;
import com.bahceden.order.domain.OrderStatusChange;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.domain.SellerOrderTransition;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.OrderPlaced;
import com.bahceden.payment.PaymentProvider.PaymentOutcome;
import com.bahceden.payment.PaymentProvider.PaymentRequest;
import com.bahceden.payment.PaymentProvider.PaymentShare;
import com.bahceden.payment.PaymentProviderException;
import com.bahceden.payment.PaymentService;
import com.bahceden.payment.domain.RefundReason;
import com.bahceden.seller.domain.Seller;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sipariş oluşturma ve ödeme tamamlama (cart-and-checkout spec'i,
 * add-cart-and-checkout D6–D8).
 *
 * <p><b>Oluşturma:</b> sepetin tamamı satın alınabilir olmalıdır. Satırlar
 * satıcıya göre bölünür; fiyat, kargo ücreti, komisyon ve hakediş
 * {@link OrderPricing} ile hesaplanıp siparişe yazılır; adres kopyalanır.
 * Stok bu adımda düşmez. Ödeme sağlayıcıda bölünmüş ödeme olarak başlatılır.
 * Aynı {@code Idempotency-Key} ikinci siparişi açmaz. Yeni sipariş yalnızca
 * e-postası doğrulanmış alıcıya açılır (add-checkout-email-verification D1).
 *
 * <p><b>Tamamlama:</b> sonuç sağlayıcıdan doğrulanır. Başarılıysa ürünler
 * kimlik sırasıyla kilitlenir ve stok düşer; stok yetmezse ödemenin tamamı
 * iade edilir. Süresi dolmuş ödeme alınmışsa iade edilir. Çağrı tekrarlanabilir.
 *
 * <p><b>Dönüşü gelmeyen sipariş:</b> temizlik işi, ödeme süresi geçmiş
 * siparişi aynı kuralla ve sistem adına sonuçlandırır
 * (add-stale-record-cleanup D1); kilit ve yeniden okuma çift kapatmayı önler.
 *
 * <p>Sağlayıcı hatası 502 döner ve transaction geri alınır: para ile kayıt
 * ayrışmaz. Log'a adres ve sipariş içeriği yazılmaz (K10).
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    /** İstemcinin ürettiği tekrar anahtarı: harf, rakam, tire, alt çizgi; 8–64 karakter. */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private static final String ORDER_NUMBER_PREFIX = "BHC-";
    private static final int ORDER_NUMBER_LENGTH = 10;
    /** Crockford base32: karıştırılan harfler (I, L, O, U) yok. */
    private static final char[] ORDER_NUMBER_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final OrderStatusChangeRepository statusChanges;
    private final CartService cartService;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final AddressService addressService;
    private final ProductRepository products;
    private final PaymentService payments;
    private final OrderPricing pricing;
    private final OrderService orderService;
    private final OrderNotifier notifier;
    private final BahcedenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    CheckoutService(OrderRepository orders, AccountRepository accounts, OrderStatusChangeRepository statusChanges, CartService cartService,
                    CartRepository carts, CartItemRepository cartItems, AddressService addressService,
                    ProductRepository products, PaymentService payments, OrderPricing pricing,
                    OrderService orderService, OrderNotifier notifier, BahcedenProperties properties, Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.statusChanges = statusChanges;
        this.cartService = cartService;
        this.carts = carts;
        this.cartItems = cartItems;
        this.addressService = addressService;
        this.products = products;
        this.payments = payments;
        this.pricing = pricing;
        this.orderService = orderService;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sepetten sipariş oluşturur ve ödemeyi başlatır.
     *
     * @throws ApiException alan bazlı {@code VALIDATION_FAILED} (Idempotency-Key), {@code ORDER_EMAIL_NOT_VERIFIED},
     *                      {@code ADDRESS_NOT_FOUND},
     *                      {@code ADDRESS_NOT_DELIVERABLE}, {@code CART_EMPTY},
     *                      {@code CART_HAS_UNAVAILABLE_ITEMS} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    @Transactional
    public OrderPlaced placeOrder(long accountId, String idempotencyKey, long addressId) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.invalidField("Idempotency-Key", "validation.idempotencyKey.format");
        }
        var existing = orders.findByBuyerAccountIdAndIdempotencyKey(accountId, idempotencyKey);
        if (existing.isPresent()) {
            var order = existing.get();
            return new OrderPlaced(order.getOrderNumber(), payments.redirectUrlOf(order.getId()).orElseThrow());
        }
        // Tekrar anahtarından sonra: oluşmuş sipariş geri alınamaz. Durum güncel kayıttan (D1, ADR-024 deseni).
        if (!accounts.findById(accountId).map(Account::isEmailVerified).orElse(false)) {
            throw new ApiException(ApiErrorCode.ORDER_EMAIL_NOT_VERIFIED, "error.orderEmailNotVerified");
        }

        var address = addressService.own(accountId, addressId);
        if (!addressService.isDeliverable(address)) {
            throw new ApiException(ApiErrorCode.ADDRESS_NOT_DELIVERABLE, "error.addressNotDeliverable");
        }
        var lines = cartService.lines(accountId);
        if (lines.isEmpty()) {
            throw new ApiException(ApiErrorCode.CART_EMPTY, "error.cartEmpty");
        }
        if (!lines.stream().allMatch(CartService::isAvailable)) {
            throw new ApiException(ApiErrorCode.CART_HAS_UNAVAILABLE_ITEMS, "error.cartHasUnavailableItems");
        }

        var now = clock.instant();
        var order = Order.open(newOrderNumber(), accountId, idempotencyKey,
                new Order.ShippingAddress(address.getRecipientName(), address.getPhone(), address.getProvinceCode(),
                        address.getDistrict(), address.getAddressLine(), address.getPostalCode().orElse(null)),
                CartService.CURRENCY, now.plus(properties.commerce().paymentTimeout()), now);
        for (var sellerLines : bySeller(lines).values()) {
            order.add(sellerOrder(order, sellerLines, now));
        }
        orders.save(order);
        for (var sellerOrder : order.getSellerOrders()) {
            statusChanges.save(OrderStatusChange.record(sellerOrder.getId(), null, SellerOrderStatus.PENDING_PAYMENT,
                    null, accountId, now));
        }

        var keys = payments.subMerchantKeys(order.getSellerOrders().stream().map(so -> so.getSeller().getId()).toList());
        var shares = order.getSellerOrders().stream()
                .map(so -> new PaymentShare(keys.get(so.getSeller().getId()), so.refundableAmount(), so.getSellerPayout()))
                .toList();
        String redirectUrl;
        try {
            redirectUrl = payments.start(order.getId(), new PaymentRequest(order.getOrderNumber(), order.getGrandTotal(),
                    order.getCurrency(), returnUrl(order), shares));
        } catch (PaymentProviderException cause) {
            log.warn("Ödeme başlatılamadı: sipariş {}", order.getOrderNumber(), cause);
            throw providerError();
        }
        log.info("Sipariş oluşturuldu: sipariş {}, {} satıcı", order.getOrderNumber(), order.getSellerOrders().size());
        return new OrderPlaced(order.getOrderNumber(), redirectUrl);
    }

    /**
     * Ödemeyi sağlayıcıdan doğrular ve siparişi sonuçlandırır; sonuçlanmış
     * siparişte mevcut durumu döner.
     *
     * @throws ApiException {@code ORDER_NOT_FOUND} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    @Transactional
    public OrderDetail completePayment(long accountId, String orderNumber) {
        var order = orders.findForPayment(orderNumber, accountId).orElseThrow(OrderService::orderNotFound);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            try {
                settle(accountId, order);
            } catch (PaymentProviderException cause) {
                log.warn("Ödeme doğrulanamadı: sipariş {}", order.getOrderNumber(), cause);
                throw providerError();
            }
        }
        return orderService.detailOf(order);
    }

    /**
     * Ödeme dönüşü hiç gelmeyen siparişi sonuçlandırır (add-stale-record-cleanup
     * D1). Sipariş kilitlenir ve yeniden okunur: bu arada dönüşle sonuçlanmışsa
     * ya da süresi dolmamışsa dokunulmaz. Süresi dolduğu için sonuç süresi dolmuş
     * (alınmış ödeme iade edilir) ya da ödeme başarısızdır; yapan taraf sistemdir.
     *
     * @return sonuçlandıysa siparişin yeni durumu
     * @throws PaymentProviderException sağlayıcıya ulaşılamadı; transaction geri alınır
     */
    @Transactional
    public Optional<OrderStatus> expireUnpaid(long orderId) {
        var order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || !order.isPaymentExpired(clock.instant())) {
            return Optional.empty();
        }
        settle(null, order);
        return Optional.of(order.getStatus());
    }

    /** @param actorAccountId ödeme dönüşünde alıcı; temizlik işinde {@code null} (sistem) */
    private void settle(Long actorAccountId, Order order) {
        var now = clock.instant();
        var outcome = payments.verify(order.getId());
        boolean expired = order.isPaymentExpired(now);
        if (outcome == PaymentOutcome.PENDING && !expired) {
            return;
        }
        if (outcome == PaymentOutcome.FAILED) {
            close(actorAccountId, order, OrderStatus.PAYMENT_FAILED, now);
            return;
        }
        if (expired) {
            if (outcome == PaymentOutcome.SUCCEEDED) {
                payments.refund(order.getId(), null, RefundReason.PAYMENT_EXPIRED, order.getGrandTotal(), Optional.empty());
            }
            close(actorAccountId, order, OrderStatus.PAYMENT_EXPIRED, now);
            return;
        }

        var quantities = new LinkedHashMap<Long, Integer>();
        for (var sellerOrder : order.getSellerOrders()) {
            for (var item : sellerOrder.getItems()) {
                quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        var locked = products.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        boolean enough = quantities.entrySet().stream().allMatch(entry ->
                locked.containsKey(entry.getKey()) && locked.get(entry.getKey()).getStockQuantity() >= entry.getValue());
        if (!enough) {
            payments.refund(order.getId(), null, RefundReason.OUT_OF_STOCK, order.getGrandTotal(), Optional.empty());
            close(actorAccountId, order, OrderStatus.CANCELLED_OUT_OF_STOCK, now);
            log.info("Stok yetmedi, ödeme iade edildi: sipariş {}", order.getOrderNumber());
            return;
        }

        quantities.forEach((productId, quantity) -> locked.get(productId).decreaseStock(quantity, now));
        order.markPaid(now);
        for (var sellerOrder : order.getSellerOrders()) {
            transition(actorAccountId, sellerOrder, SellerOrderTransition.PAY, now);
            sellerOrder.scheduleDispatch(BusinessDays.endOfBusinessDay(now, sellerOrder.getDispatchDays()));
        }
        carts.findByAccountId(order.getBuyerAccountId()).ifPresent(cart -> cartItems.deleteProducts(cart.getId(), quantities.keySet()));
        notifier.orderPaid(order, order.getSellerOrders());
        log.info("Ödeme alındı: sipariş {}", order.getOrderNumber());
    }

    private void close(Long actorAccountId, Order order, OrderStatus outcome, Instant now) {
        order.close(outcome, now);
        for (var sellerOrder : order.getSellerOrders()) {
            transition(actorAccountId, sellerOrder, SellerOrderTransition.VOID, now);
        }
    }

    private void transition(Long actorAccountId, SellerOrder sellerOrder, SellerOrderTransition transition, Instant now) {
        var previous = sellerOrder.getStatus();
        sellerOrder.apply(transition, now);
        statusChanges.save(actorAccountId == null
                ? OrderStatusChange.recordBySystem(sellerOrder.getId(), previous, sellerOrder.getStatus(), now)
                : OrderStatusChange.record(sellerOrder.getId(), previous, sellerOrder.getStatus(), null,
                        actorAccountId, now));
    }

    private SellerOrder sellerOrder(Order order, List<CartItem> lines, Instant now) {
        Seller seller = lines.getFirst().getProduct().getSeller();
        var items = new ArrayList<OrderItem>();
        int dispatchDays = 0;
        long itemsTotal = 0;
        for (var line : lines) {
            var product = line.getProduct();
            var item = OrderItem.of(product.getId(), product.getName(), product.getSlug(), product.getUnit(),
                    product.getPrice().amountInMinorUnits(), line.getQuantity(), now);
            items.add(item);
            itemsTotal += item.getLineTotal();
            dispatchDays = Math.max(dispatchDays, product.getDispatchDays());
        }
        return SellerOrder.open(order, seller, items, pricing.shippingFee(itemsTotal), pricing.commission(itemsTotal),
                dispatchDays, now);
    }

    private static Map<Long, List<CartItem>> bySeller(List<CartItem> lines) {
        Map<Long, List<CartItem>> groups = new LinkedHashMap<>();
        for (var line : lines) {
            groups.computeIfAbsent(line.getProduct().getSeller().getId(), id -> new ArrayList<>()).add(line);
        }
        return groups;
    }

    private String returnUrl(Order order) {
        return properties.webOrigin() + "/checkout/return?order=" + order.getOrderNumber();
    }

    /** Tahmin edilemez sipariş numarası; çakışma olasılığı düşüktür, yine de denetlenir. */
    private String newOrderNumber() {
        String candidate;
        do {
            var builder = new StringBuilder(ORDER_NUMBER_PREFIX);
            for (int i = 0; i < ORDER_NUMBER_LENGTH; i++) {
                builder.append(ORDER_NUMBER_ALPHABET[random.nextInt(ORDER_NUMBER_ALPHABET.length)]);
            }
            candidate = builder.toString();
        } while (orders.existsByOrderNumber(candidate));
        return candidate;
    }

    private static ApiException providerError() {
        return new ApiException(ApiErrorCode.PAYMENT_PROVIDER_ERROR, "error.paymentProvider");
    }
}
