package com.bahceden.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Sağlayıcıdan yapılmış bir iade (add-cart-and-checkout D8, D9). Satıcı
 * siparişi kimliği boşsa ödemenin tamamı iade edilmiştir. Kayıt silinmez.
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "seller_order_id")
    private Long sellerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundReason reason;

    @Column(nullable = false)
    private long amount;

    @Column(name = "provider_reference", nullable = false, length = 100)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Refund() {
    }

    /** @param sellerOrderId yalnızca bir satıcı payı iade edildiyse dolu */
    public static Refund of(long paymentId, Long sellerOrderId, RefundReason reason, long amount,
                            String providerReference, Instant now) {
        var refund = new Refund();
        refund.paymentId = paymentId;
        refund.sellerOrderId = sellerOrderId;
        refund.reason = reason;
        refund.amount = amount;
        refund.providerReference = providerReference;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
    }
}
