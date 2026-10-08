package com.bahceden.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

/**
 * Satıcı kaydının bir durum geçişi: kim, ne zaman, hangi durumdan hangisine,
 * neden (ADR-037 §7, design.md D1).
 *
 * <p>Yalnızca eklenir. Değiştirici metodu yoktur ve repository'si silme veya
 * güncelleme sunmaz; geçmiş yasal sorumluluğun denetim izidir.
 */
@Entity
@Table(name = "seller_status_changes")
public class SellerStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** Başvuruda boş: kayıt bu geçişle doğar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SellerStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SellerStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_account_id", nullable = false)
    private Long actorAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerStatusChange() {
    }

    /**
     * @param fromStatus başvuruda {@code null}
     * @param reason     gerekçe istemeyen geçişte {@code null}
     */
    public static SellerStatusChange record(long sellerId, SellerStatus fromStatus, SellerStatus toStatus,
                                            String reason, long actorAccountId, Instant now) {
        var change = new SellerStatusChange();
        change.sellerId = sellerId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.reason = reason;
        change.actorAccountId = actorAccountId;
        change.createdAt = now;
        change.updatedAt = now;
        return change;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Optional<SellerStatus> getFromStatus() {
        return Optional.ofNullable(fromStatus);
    }

    public SellerStatus getToStatus() {
        return toStatus;
    }

    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
