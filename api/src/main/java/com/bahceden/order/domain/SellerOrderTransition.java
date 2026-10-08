package com.bahceden.order.domain;

import java.util.Set;

/**
 * Satıcı siparişinin izinli geçişleri, tek yerde (add-cart-and-checkout D6;
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
    CANCEL(Set.of(SellerOrderStatus.NEW), SellerOrderStatus.CANCELLED);

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
