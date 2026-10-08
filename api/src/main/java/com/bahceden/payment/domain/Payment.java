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
 * Bir siparişin bölünmüş ödemesi (ADR-054, add-cart-and-checkout D8).
 *
 * <p>Sipariş bir kimlik olarak tutulur: {@code payment} domaini {@code order}'ı
 * bilmez. Sonuç istemciden değil sağlayıcıdan doğrulanır; yönlendirme adresi
 * aynı siparişin tekrar isteğinde yeniden kullanılır.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_reference", nullable = false, length = 100)
    private String providerReference;

    @Column(name = "redirect_url", nullable = false, length = 500)
    private String redirectUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment initiated(long orderId, String provider, String providerReference, String redirectUrl,
                                    long amount, String currency, Instant now) {
        var payment = new Payment();
        payment.orderId = orderId;
        payment.provider = provider;
        payment.providerReference = providerReference;
        payment.redirectUrl = redirectUrl;
        payment.status = PaymentStatus.INITIATED;
        payment.amount = amount;
        payment.currency = currency;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void settle(PaymentStatus outcome, Instant now) {
        if (outcome == PaymentStatus.INITIATED) {
            throw new IllegalArgumentException("Sonuç değil: " + outcome);
        }
        status = outcome;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }
}
