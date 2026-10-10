package com.bahceden.shipping.domain;

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
 * Bir satıcı siparişinin kargo gönderisi (ADR-057, add-shipments D2).
 *
 * <p>İptal edilen gönderi silinmez; satıcı siparişinin aynı anda tek etkin
 * (iptal edilmemiş) gönderisi olur, bunu veritabanındaki kısmi benzersiz indeks
 * de zorunlu kılar. Satıcı siparişine ilişki yerine kimliğiyle bağlanır:
 * {@code shipping} paketi sipariş entity'sini taşımaz.
 *
 * <p>Takip numarası log'a yazılmaz; log'a yalnızca kimlik gider (K10).
 */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_order_id", nullable = false)
    private Long sellerOrderId;

    @Column(nullable = false, length = 20)
    private String integrator;

    @Column(name = "integrator_ref", nullable = false, length = 100)
    private String integratorRef;

    @Column(nullable = false, length = 80)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, length = 60)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_size", nullable = false, length = 10)
    private PackageSize packageSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    @Column(name = "label_key", nullable = false, length = 32)
    private String labelKey;

    @Column(name = "label_content_type", nullable = false, length = 40)
    private String labelContentType;

    @Column(name = "handed_over_at")
    private Instant handedOverAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {
    }

    /**
     * Entegratörde açılmış gönderinin kaydı.
     *
     * @param labelKey         etiketin depolama anahtarı
     * @param labelContentType etiketin içerikten tespit edilen türü
     */
    public static Shipment created(long sellerOrderId, String integrator, String integratorRef, String carrier,
                                   String trackingNumber, PackageSize packageSize, String labelKey,
                                   String labelContentType, Instant now) {
        var shipment = new Shipment();
        shipment.sellerOrderId = sellerOrderId;
        shipment.integrator = integrator;
        shipment.integratorRef = integratorRef;
        shipment.carrier = carrier;
        shipment.trackingNumber = trackingNumber;
        shipment.packageSize = packageSize;
        shipment.status = ShipmentStatus.CREATED;
        shipment.labelKey = labelKey;
        shipment.labelContentType = labelContentType;
        shipment.createdAt = now;
        shipment.updatedAt = now;
        return shipment;
    }

    /**
     * Entegratörün bildirdiği durumu uygular. Geri giden veya aynı bildirim ve
     * kapanmış gönderiye gelen bildirim yok sayılır; iptal bildirimi yalnızca
     * kargoya verilmemiş gönderiye uygulanır (D5, D6).
     *
     * @return durum değiştiyse {@code true}
     */
    public boolean advanceTo(ShipmentStatus reported, Instant now) {
        lastPolledAt = now;
        if (!status.isOpen()) {
            return false;
        }
        if (reported == ShipmentStatus.CANCELLED) {
            if (status != ShipmentStatus.CREATED) {
                return false;
            }
            status = ShipmentStatus.CANCELLED;
            updatedAt = now;
            return true;
        }
        if (!status.isBefore(reported)) {
            return false;
        }
        if (reported.isHandedOver() && handedOverAt == null) {
            handedOverAt = now;
        }
        if (reported == ShipmentStatus.DELIVERED) {
            deliveredAt = now;
        }
        status = reported;
        updatedAt = now;
        return true;
    }

    /** Sorgu başarısız oldu; gönderi sıranın sonuna geçer, durum değişmez (D5). */
    public void markPolled(Instant now) {
        lastPolledAt = now;
    }

    /** Kargoya verilmeden iptal (D6). */
    public void cancel(Instant now) {
        if (status != ShipmentStatus.CREATED) {
            throw new IllegalStateException("Kargoya verilmiş gönderi iptal edilemez: " + status);
        }
        status = ShipmentStatus.CANCELLED;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerOrderId() {
        return sellerOrderId;
    }

    public String getIntegratorRef() {
        return integratorRef;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public PackageSize getPackageSize() {
        return packageSize;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getLabelContentType() {
        return labelContentType;
    }

    public Optional<Instant> getHandedOverAt() {
        return Optional.ofNullable(handedOverAt);
    }

    public Optional<Instant> getDeliveredAt() {
        return Optional.ofNullable(deliveredAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
