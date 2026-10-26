package com.bahceden.order;

import com.bahceden.catalog.ProductRepository;
import com.bahceden.catalog.domain.Product;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.order.domain.CancelledBy;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.OrderStatusChange;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderTransition;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.payment.PaymentProviderException;
import com.bahceden.payment.PaymentService;
import com.bahceden.payment.domain.RefundReason;
import com.bahceden.seller.SellerService;
import com.bahceden.shipping.ShipmentService;
import com.bahceden.shipping.ShipmentStatusPoller;
import com.bahceden.shipping.ShippingIntegratorException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kargodan önce iptal ve tam iade (orders spec'i, ADR-056 §1–2,
 * add-cart-and-checkout D9).
 *
 * <p>Alıcı kargoya verilmemiş satıcı siparişini her zaman, gerekçe isteğe
 * bağlı iptal eder; satıcı gerekçeyle iptal eder. Tek transaction'da: satıcı
 * siparişi kilitlenir ve {@code CANCELLED} olur, ürünlerin stoğu geri eklenir,
 * geçmiş yazılır, satıcı siparişinin ürün tutarı ile kargo ücreti sağlayıcıdan
 * iade edilir. Gönderi açılmışsa önce gönderi entegratörde iptal edilir
 * (add-shipments D6): para dönüp paket yola çıkmasın diye iadeden önce.
 * Entegratör veya iade başarısız olursa hiçbir şey değişmez (502). Karşı tarafa
 * e-posta transaction sonrasında gider. Siparişin diğer satıcı siparişleri
 * etkilenmez.
 *
 * <p>Entegratör gönderi iptalini reddederse, iptal transaction'ı geri
 * alındıktan sonra gönderinin durumu hemen okunur
 * (change-cancel-rejection-syncs-shipment D1): paket kargoya verilmişse
 * gönderi ve satıcı siparişi durum sorgusunun kurallarıyla güncellenir ve
 * yanıt 409 {@code ORDER_ALREADY_SHIPPED} olur. Okuma iptal transaction'ının
 * dışındadır; içeride yapılsa ya geri alınır ya da aynı satırların kilidini
 * bekler. Bu yüzden transaction sınırı {@link TransactionTemplate} ile çizilir.
 */
@Service
public class OrderCancellationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);

    /** Satıcı gerekçesinin en kısa uzunluğu; satıcı kararlarındaki gerekçeyle aynı ölçüt. */
    private static final int MIN_SELLER_REASON = 10;

    private final OrderRepository orders;
    private final SellerOrderRepository sellerOrders;
    private final OrderStatusChangeRepository statusChanges;
    private final ProductRepository products;
    private final PaymentService payments;
    private final SellerService sellers;
    private final OrderService orderService;
    private final OrderNotifier notifier;
    private final ShipmentService shipments;
    private final ShipmentStatusPoller shipmentStatus;
    private final TransactionTemplate transactions;
    private final Clock clock;

    OrderCancellationService(OrderRepository orders, SellerOrderRepository sellerOrders,
                             OrderStatusChangeRepository statusChanges, ProductRepository products,
                             PaymentService payments, SellerService sellers, OrderService orderService,
                             OrderNotifier notifier, ShipmentService shipments, ShipmentStatusPoller shipmentStatus,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.orders = orders;
        this.sellerOrders = sellerOrders;
        this.statusChanges = statusChanges;
        this.products = products;
        this.payments = payments;
        this.sellers = sellers;
        this.orderService = orderService;
        this.notifier = notifier;
        this.shipments = shipments;
        this.shipmentStatus = shipmentStatus;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @throws ApiException {@code ORDER_NOT_FOUND}, {@code SELLER_ORDER_NOT_FOUND},
     *                      {@code ORDER_INVALID_TRANSITION}, {@code ORDER_ALREADY_SHIPPED},
     *                      {@code SHIPPING_INTEGRATOR_ERROR} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    public OrderDetail cancelByBuyer(long accountId, String orderNumber, long sellerOrderId, String reason) {
        return syncingShipmentOnRefusal(() -> {
            var order = orders.findByOrderNumberAndBuyerAccountId(orderNumber, accountId)
                    .orElseThrow(OrderService::orderNotFound);
            var sellerOrder = sellerOrders.findForBuyerCancel(sellerOrderId, order.getId())
                    .orElseThrow(OrderService::sellerOrderNotFound);
            cancel(sellerOrder, CancelledBy.BUYER, Optional.ofNullable(reason).map(String::trim)
                    .filter(text -> !text.isEmpty()).orElse(null), accountId);
            return orderService.detailOf(order);
        });
    }

    /**
     * @throws ApiException alan bazlı {@code VALIDATION_FAILED} (gerekçe), {@code SELLER_ORDER_NOT_FOUND},
     *                      {@code ORDER_INVALID_TRANSITION}, {@code ORDER_ALREADY_SHIPPED},
     *                      {@code SHIPPING_INTEGRATOR_ERROR} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    public SellerOrderDetail cancelBySeller(long accountId, long sellerOrderId, String reason) {
        return syncingShipmentOnRefusal(() -> {
            var seller = sellers.requireApprovedSeller(accountId);
            var text = Optional.ofNullable(reason).map(String::trim).orElse("");
            if (text.isEmpty()) {
                throw ApiException.invalidField("reason", "validation.cancelReason.required");
            }
            if (text.length() < MIN_SELLER_REASON) {
                throw ApiException.invalidField("reason", "validation.cancelReason.length");
            }
            var sellerOrder = sellerOrders.findForSellerUpdate(sellerOrderId, seller.getId())
                    .filter(found -> OrderService.SELLER_VISIBLE.contains(found.getStatus()))
                    .orElseThrow(OrderService::sellerOrderNotFound);
            cancel(sellerOrder, CancelledBy.SELLER, text, accountId);
            return orderService.detailForSeller(accountId, sellerOrderId);
        });
    }

    /**
     * İptali tek transaction'da yapar. Entegratör gönderi iptalini reddederse
     * transaction geri alınır, ardından gönderinin durumu okunur (D1).
     */
    private <T> T syncingShipmentOnRefusal(Supplier<T> cancellation) {
        try {
            return transactions.execute(status -> cancellation.get());
        } catch (ShipmentCancelRefused refused) {
            if (shipmentStatus.refreshActive(refused.sellerOrderId)) {
                log.info("İptal reddedildi, paket kargoya verilmiş: satıcı siparişi {}", refused.sellerOrderId);
                throw new ApiException(ApiErrorCode.ORDER_ALREADY_SHIPPED, "error.orderAlreadyShipped");
            }
            throw new ApiException(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR, "error.shippingIntegrator");
        }
    }

    private void cancel(SellerOrder sellerOrder, CancelledBy by, String reason, long actorAccountId) {
        if (!sellerOrder.allows(SellerOrderTransition.CANCEL)) {
            throw new ApiException(ApiErrorCode.ORDER_INVALID_TRANSITION, "error.orderInvalidTransition");
        }
        try {
            shipments.cancelActive(sellerOrder.getId());
        } catch (ShippingIntegratorException cause) {
            log.warn("Gönderi iptal edilemedi, sipariş iptali yapılmadı: satıcı siparişi {}", sellerOrder.getId(), cause);
            throw new ShipmentCancelRefused(sellerOrder.getId());
        }
        var now = clock.instant();
        var quantities = sellerOrder.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
        Map<Long, Product> locked = products.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        quantities.forEach((productId, quantity) -> {
            var product = locked.get(productId);
            if (product != null) {
                product.increaseStock(quantity, now);
            }
        });

        var previous = sellerOrder.getStatus();
        sellerOrder.cancel(by, reason, now);
        statusChanges.save(OrderStatusChange.record(sellerOrder.getId(), previous, sellerOrder.getStatus(), reason,
                actorAccountId, now));

        var order = sellerOrder.getOrder();
        try {
            payments.refund(order.getId(), sellerOrder.getId(), RefundReason.CANCELLED, sellerOrder.refundableAmount(),
                    payments.subMerchantKeyOf(sellerOrder.getSeller().getId()));
        } catch (PaymentProviderException cause) {
            log.warn("İade yapılamadı, iptal geri alındı: sipariş {}", order.getOrderNumber(), cause);
            throw new ApiException(ApiErrorCode.PAYMENT_PROVIDER_ERROR, "error.paymentProvider");
        }
        notifier.sellerOrderCancelled(sellerOrder, by, reason);
        log.info("Satıcı siparişi iptal edildi: sipariş {}, satıcı siparişi {}, {}", order.getOrderNumber(),
                sellerOrder.getId(), by);
    }

    /** Entegratör gönderi iptalini reddetti; transaction'ı geri aldırır, yanıtı çağıran seçer. */
    private static final class ShipmentCancelRefused extends RuntimeException {

        private final long sellerOrderId;

        ShipmentCancelRefused(long sellerOrderId) {
            super(null, null, false, false);
            this.sellerOrderId = sellerOrderId;
        }
    }
}
