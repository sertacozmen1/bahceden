package com.bahceden.order.domain;

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
 * Satıcı siparişinin bir durum geçişi: kim, ne zaman, hangi durumdan
 * hangisine, neden (orders spec'i, add-cart-and-checkout D6).
 *
 * <p>Yalnızca eklenir (ADR-040 deseni): değiştirici metodu yoktur ve
 * repository'si silme veya güncelleme sunmaz. Gönderi durumu sorgusunun
 * yaptığı geçişlerde yapan hesap yoktur; bu satırlarda hesap boştur ve
 * "sistem" okunur (add-shipments D2).
 */
@Entity
@Table(name = "order_status_changes")
public class OrderStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_order_id", nullable = false)
    private Long sellerOrderId;

    /** Sipariş oluşurken boş: satıcı siparişi bu geçişle doğar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SellerOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SellerOrderStatus toStatus;

    @Column(length = 500)
    private String reason;

    /** Boşsa geçişi sistem yaptı (add-shipments D2). */
    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderStatusChange() {
    }

    public static OrderStatusChange record(long sellerOrderId, SellerOrderStatus fromStatus, SellerOrderStatus toStatus,
                                           String reason, long actorAccountId, Instant now) {
        return create(sellerOrderId, fromStatus, toStatus, reason, actorAccountId, now);
    }

    /** Yapan hesabı olmayan geçiş: gönderi durumu sorgusu (add-shipments D2, D5). */
    public static OrderStatusChange recordBySystem(long sellerOrderId, SellerOrderStatus fromStatus,
                                                   SellerOrderStatus toStatus, Instant now) {
        return create(sellerOrderId, fromStatus, toStatus, null, null, now);
    }

    private static OrderStatusChange create(long sellerOrderId, SellerOrderStatus fromStatus,
                                            SellerOrderStatus toStatus, String reason, Long actorAccountId,
                                            Instant now) {
        var change = new OrderStatusChange();
        change.sellerOrderId = sellerOrderId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.reason = reason;
        change.actorAccountId = actorAccountId;
        change.createdAt = now;
        change.updatedAt = now;
        return change;
    }

    public Long getSellerOrderId() {
        return sellerOrderId;
    }

    public Optional<SellerOrderStatus> getFromStatus() {
        return Optional.ofNullable(fromStatus);
    }

    public SellerOrderStatus getToStatus() {
        return toStatus;
    }
}
