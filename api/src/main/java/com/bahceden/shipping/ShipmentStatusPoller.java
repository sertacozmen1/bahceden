package com.bahceden.shipping;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.order.ShipmentProgressService;
import com.bahceden.shipping.domain.Shipment;
import com.bahceden.shipping.domain.ShipmentStatus;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Açık gönderilerin durumunu entegratörden sorgular (shipments spec'i,
 * add-shipments D5).
 *
 * <p>Bir turda en uzun süredir sorgulanmayan gönderilerden yapılandırılmış
 * sayıda alınır. Her gönderi kendi transaction'ında kilitlenip güncellenir:
 * bir gönderinin hatası diğerlerini durdurmaz, o gönderi sıranın sonuna geçer
 * ve sonraki turda yeniden denenir. Entegratör ara durumları atlarsa satıcı
 * siparişinin geçişleri sırayla yapılır ({@code SHIPPED} sonra {@code DELIVERED}).
 *
 * <p>Tek örnek varsayılır (AGENTS.md §3); iki örnek aynı gönderiyi işlese de
 * satır kilidi ve "geri giden bildirim yok sayılır" kuralı çift geçişi önler.
 */
@Component
public class ShipmentStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusPoller.class);

    private static final Set<ShipmentStatus> OPEN =
            EnumSet.of(ShipmentStatus.CREATED, ShipmentStatus.HANDED_OVER, ShipmentStatus.IN_TRANSIT);

    private final ShipmentRepository shipments;
    private final ShippingIntegrator integrator;
    private final ShipmentProgressService progress;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final Clock clock;

    ShipmentStatusPoller(ShipmentRepository shipments, ShippingIntegrator integrator, ShipmentProgressService progress,
                         PlatformTransactionManager transactionManager, BahcedenProperties properties, Clock clock) {
        this.shipments = shipments;
        this.integrator = integrator;
        this.progress = progress;
        this.transactions = new TransactionTemplate(transactionManager);
        this.batchSize = properties.shipping().pollBatchSize();
        this.clock = clock;
    }

    /**
     * Tek tur sorgu.
     *
     * @return durumu değişen gönderi sayısı
     */
    public int pollOnce() {
        var ids = shipments.findIdsToPoll(OPEN, PageRequest.of(0, batchSize));
        int changed = 0;
        for (var id : ids) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> pollOne(id)))) {
                    changed++;
                }
            } catch (RuntimeException cause) {
                log.warn("Gönderi durumu alınamadı, sonraki turda yeniden denenecek: gönderi {}", id, cause);
                transactions.executeWithoutResult(status ->
                        shipments.findByIdForUpdate(id).ifPresent(shipment -> shipment.markPolled(clock.instant())));
            }
        }
        return changed;
    }

    /**
     * Satıcı siparişinin etkin gönderisinin durumunu hemen okur ve sorgu
     * kurallarıyla uygular (change-cancel-rejection-syncs-shipment D1). İptal
     * entegratörde reddedildikten sonra, iptal transaction'ı geri alınmışken
     * çağrılır; kendi transaction'ında çalışır.
     *
     * @return gönderi artık kargoya verilmişse {@code true}; gönderi yoksa veya
     *         durum okunamazsa {@code false}
     */
    public boolean refreshActive(long sellerOrderId) {
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> shipments.findActiveForUpdate(sellerOrderId)
                    .map(shipment -> {
                        if (shipment.getStatus().isOpen()) {
                            apply(shipment);
                        }
                        return shipment.getStatus().isHandedOver();
                    })
                    .orElse(false)));
        } catch (ShippingIntegratorException cause) {
            log.warn("Gönderi durumu iptal reddinden sonra okunamadı: satıcı siparişi {}", sellerOrderId, cause);
            return false;
        }
    }

    private boolean pollOne(long id) {
        var shipment = shipments.findByIdForUpdate(id).orElse(null);
        if (shipment == null || !shipment.getStatus().isOpen()) {
            return false;
        }
        return apply(shipment);
    }

    private boolean apply(Shipment shipment) {
        var id = shipment.getId();
        var previous = shipment.getStatus();
        var reported = integrator.status(shipment.getIntegratorRef());
        var now = clock.instant();
        if (!shipment.advanceTo(reported, now)) {
            return false;
        }
        var sellerOrderId = shipment.getSellerOrderId();
        if (!previous.isHandedOver() && reported.isHandedOver()) {
            progress.shipped(sellerOrderId, shipment.getCarrier(), shipment.getTrackingNumber(), now);
        }
        if (reported == ShipmentStatus.DELIVERED) {
            progress.delivered(sellerOrderId, now);
        } else if (reported == ShipmentStatus.DELIVERY_FAILED) {
            progress.deliveryFailed(sellerOrderId, now);
        }
        log.info("Gönderi durumu değişti: gönderi {}, {} -> {}", id, previous, reported);
        return true;
    }
}
