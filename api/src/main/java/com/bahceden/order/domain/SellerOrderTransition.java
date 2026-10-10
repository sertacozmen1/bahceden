package com.bahceden.order.domain;

import java.util.Set;

/**
 * Satıcı siparişinin izinli geçişleri, tek yerde (add-cart-and-checkout D6,
 * add-shipments D2;
 * {@code ProductTransition} ile aynı desen).
 *
 * <p>Kimin hangi geçişi yapabileceği serviste denetlenir; burada yalnızca
 * geçişin hangi durumlardan yapılabileceği tutulur.
 */
public enum SellerOrderTransition {

    /** Ödeme doğrulandı ve stok düştü. */
    PAY(Set.of(SellerOrderStatus.PENDING_PAYMENT), SellerOrderStatus.NEW),

    /** Ödeme başarısız, süresi dolmuş veya stok yetmedi. */
    VOID(Set.of(SellerOrderStatus.PENDING_PAYMENT), SellerOrderStatus.VOID),

    /** Kargodan önce iptal (ADR-056 §1–2). */
    CANCEL(Set.of(SellerOrderStatus.NEW), SellerOrderStatus.CANCELLED),

    /** Kargo firması paketi teslim aldı. */
    SHIP(Set.of(SellerOrderStatus.NEW), SellerOrderStatus.SHIPPED),

    /** Alıcıya teslim edildi. */
    DELIVER(Set.of(SellerOrderStatus.SHIPPED), SellerOrderStatus.DELIVERED),

    /** Kargo teslim edemedi. */
    FAIL_DELIVERY(Set.of(SellerOrderStatus.SHIPPED), SellerOrderStatus.DELIVERY_FAILED),

    /** Teslim edilemeyen sipariş için admin tam iade kararı (add-shipments D7). */
    REFUND_UNDELIVERED(Set.of(SellerOrderStatus.DELIVERY_FAILED), SellerOrderStatus.REFUNDED),

    /** Teslim edilemeyen sipariş için admin iadesiz kapatma kararı (add-shipments D7). */
    CLOSE_UNDELIVERED(Set.of(SellerOrderStatus.DELIVERY_FAILED), SellerOrderStatus.CLOSED);

    private final Set<SellerOrderStatus> from;
    private final SellerOrderStatus to;

    SellerOrderTransition(Set<SellerOrderStatus> from, SellerOrderStatus to) {
        this.from = from;
        this.to = to;
    }

    public boolean allowsFrom(SellerOrderStatus status) {
        return from.contains(status);
    }

    public SellerOrderStatus to() {
        return to;
    }
}
