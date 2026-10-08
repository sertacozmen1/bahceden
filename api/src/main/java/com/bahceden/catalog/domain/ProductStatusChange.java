package com.bahceden.catalog.domain;

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
 * İlanın bir durum geçişi: kim, ne zaman, hangi durumdan hangisine, neden
 * (add-product-listings D1). Satıcı kaydının geçmişiyle aynı desen (ADR-040).
 *
 * <p>Yalnızca eklenir. Değiştirici metodu yoktur ve repository'si silme veya
 * güncelleme sunmaz.
 */
@Entity
@Table(name = "product_status_changes")
public class ProductStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Oluşturmada boş: ilan bu geçişle doğar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ProductStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ProductStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_account_id", nullable = false)
    private Long actorAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductStatusChange() {
    }

    /**
     * @param fromStatus oluşturmada {@code null}
     * @param reason     gerekçe istemeyen geçişte {@code null}
     */
    public static ProductStatusChange record(long productId, ProductStatus fromStatus, ProductStatus toStatus,
                                            String reason, long actorAccountId, Instant now) {
        var change = new ProductStatusChange();
        change.productId = productId;
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

    public Long getProductId() {
        return productId;
    }

    public Optional<ProductStatus> getFromStatus() {
        return Optional.ofNullable(fromStatus);
    }

    public ProductStatus getToStatus() {
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
