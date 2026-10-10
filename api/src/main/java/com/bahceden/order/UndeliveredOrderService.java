package com.bahceden.order;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.OrderStatusChange;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.domain.SellerOrderTransition;
import com.bahceden.order.dto.AdminSellerOrderDetail;
import com.bahceden.order.dto.AdminSellerOrderSummary;
import com.bahceden.order.dto.UndeliveredResolutionRequest.Decision;
import com.bahceden.payment.PaymentProviderException;
import com.bahceden.payment.PaymentService;
import com.bahceden.payment.domain.RefundReason;
import com.bahceden.shipping.ShipmentService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teslim edilemeyen satıcı siparişi için admin kararı (shipments spec'i,
 * add-shipments D7; ürün sahibinin 2026-10-10 kararı).
 *
 * <p>{@code REFUND}: satıcı siparişinin ürün tutarı ile kargo ücreti
 * sağlayıcıdan iade edilir; stok geri eklenmez (taze ürün dönüşte satılabilir
 * sayılmaz). İade başarısız olursa karar gerçekleşmez (502). {@code CLOSE}:
 * gerekçe zorunlu, para hareketi yok, hakediş geçerli kalır. Karar ve gerekçe
 * geçmişe admin hesabıyla yazılır; alıcıya ve satıcıya e-posta transaction
 * sonrasında gider.
 */
@Service
public class UndeliveredOrderService {

    private static final Logger log = LoggerFactory.getLogger(UndeliveredOrderService.class);

    /** Admin'in görebildiği durumlar: karar bekleyen ve karara bağlanmış teslim edilemeyenler. */
    static final Set<SellerOrderStatus> ADMIN_VISIBLE =
            EnumSet.of(SellerOrderStatus.DELIVERY_FAILED, SellerOrderStatus.REFUNDED, SellerOrderStatus.CLOSED);

    /** Kapatma gerekçesinin en kısa uzunluğu; satıcı iptal gerekçesiyle aynı ölçüt. */
    private static final int MIN_CLOSE_REASON = 10;

    private final SellerOrderRepository sellerOrders;
    private final OrderStatusChangeRepository statusChanges;
    private final PaymentService payments;
    private final ShipmentService shipments;
    private final OrderMapper mapper;
    private final OrderNotifier notifier;
    private final Clock clock;

    UndeliveredOrderService(SellerOrderRepository sellerOrders, OrderStatusChangeRepository statusChanges,
                            PaymentService payments, ShipmentService shipments, OrderMapper mapper,
                            OrderNotifier notifier, Clock clock) {
        this.sellerOrders = sellerOrders;
        this.statusChanges = statusChanges;
        this.payments = payments;
        this.shipments = shipments;
        this.mapper = mapper;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * @param status {@code DELIVERY_FAILED}, {@code REFUNDED} veya {@code CLOSED}
     * @throws ApiException {@code VALIDATION_FAILED} (status alanı) başka bir durumda
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminSellerOrderSummary> list(SellerOrderStatus status, int page, int size) {
        if (!ADMIN_VISIBLE.contains(status)) {
            throw ApiException.invalidField("status", "validation.adminOrderStatus.allowed");
        }
        return PageResponse.from(sellerOrders.findForAdmin(status,
                PageRequest.of(page, Math.min(size, OrderService.MAX_PAGE_SIZE))).map(mapper::toAdminSummary));
    }

    /** @throws ApiException {@code SELLER_ORDER_NOT_FOUND} */
    @Transactional(readOnly = true)
    public AdminSellerOrderDetail detail(long sellerOrderId) {
        var sellerOrder = sellerOrders.findForAdminDetail(sellerOrderId, ADMIN_VISIBLE)
                .orElseThrow(OrderService::sellerOrderNotFound);
        return mapper.toAdminDetail(sellerOrder, shipments.activeView(sellerOrderId));
    }

    /**
     * @throws ApiException alan bazlı {@code VALIDATION_FAILED} (gerekçe), {@code SELLER_ORDER_NOT_FOUND},
     *                      {@code ORDER_INVALID_TRANSITION} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    @Transactional
    public AdminSellerOrderDetail resolve(long adminAccountId, long sellerOrderId, Decision decision, String reason) {
        var text = Optional.ofNullable(reason).map(String::trim).filter(value -> !value.isEmpty());
        if (decision == Decision.CLOSE) {
            if (text.isEmpty()) {
                throw ApiException.invalidField("reason", "validation.decisionReason.required");
            }
            if (text.get().length() < MIN_CLOSE_REASON) {
                throw ApiException.invalidField("reason", "validation.decisionReason.length");
            }
        }
        var sellerOrder = sellerOrders.findByIdForUpdate(sellerOrderId)
                .filter(found -> OrderService.SELLER_VISIBLE.contains(found.getStatus()))
                .orElseThrow(OrderService::sellerOrderNotFound);
        var transition = decision == Decision.REFUND
                ? SellerOrderTransition.REFUND_UNDELIVERED
                : SellerOrderTransition.CLOSE_UNDELIVERED;
        if (!sellerOrder.allows(transition)) {
            throw new ApiException(ApiErrorCode.ORDER_INVALID_TRANSITION, "error.resolutionNotAllowed");
        }
        var now = clock.instant();
        var previous = sellerOrder.getStatus();
        sellerOrder.apply(transition, now);
        statusChanges.save(OrderStatusChange.record(sellerOrderId, previous, sellerOrder.getStatus(),
                text.orElse(null), adminAccountId, now));
        if (decision == Decision.REFUND) {
            var order = sellerOrder.getOrder();
            try {
                payments.refund(order.getId(), sellerOrderId, RefundReason.UNDELIVERED, sellerOrder.refundableAmount(),
                        payments.subMerchantKeyOf(sellerOrder.getSeller().getId()));
            } catch (PaymentProviderException cause) {
                log.warn("İade yapılamadı, karar geri alındı: satıcı siparişi {}", sellerOrderId, cause);
                throw new ApiException(ApiErrorCode.PAYMENT_PROVIDER_ERROR, "error.paymentProvider");
            }
        }
        notifier.undeliveredResolved(sellerOrder, decision == Decision.REFUND, text.orElse(null));
        log.info("Teslim edilemeyen sipariş karara bağlandı: satıcı siparişi {}, {}", sellerOrderId, decision);
        return detail(sellerOrderId);
    }
}
