package com.bahceden.order;

import com.bahceden.order.domain.OrderStatusChange;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderTransition;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gönderi durumunun satıcı siparişine yansıması (shipments spec'i "Gönderi
 * durumu entegratörden izlenir", add-shipments D2, D5, D8).
 *
 * <p>Gönderi durumu sorgusu ({@code shipping} paketi) çağırır; geçiş mantığı
 * sipariş paketinde kalır. Her metot çağıranın transaction'ında çalışır,
 * satıcı siparişini kilitler ve geçiş izinli değilse (zaten yapılmış veya
 * sipariş başka bir durumda) hiçbir şey yapmaz: aynı bildirimin ikinci kez
 * gelmesi çift geçiş üretmez. Geçmişe yapan hesap olmadan, "sistem" olarak
 * yazılır.
 */
@Service
public class ShipmentProgressService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentProgressService.class);

    private final SellerOrderRepository sellerOrders;
    private final OrderStatusChangeRepository statusChanges;
    private final OrderNotifier notifier;

    ShipmentProgressService(SellerOrderRepository sellerOrders, OrderStatusChangeRepository statusChanges,
                            OrderNotifier notifier) {
        this.sellerOrders = sellerOrders;
        this.statusChanges = statusChanges;
        this.notifier = notifier;
    }

    /** Kargo firması paketi teslim aldı: {@code SHIPPED}; alıcıya firma ve takip numarasıyla e-posta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shipped(long sellerOrderId, String carrier, String trackingNumber, Instant now) {
        apply(sellerOrderId, SellerOrderTransition.SHIP, now)
                .ifPresent(sellerOrder -> notifier.sellerOrderShipped(sellerOrder, carrier, trackingNumber));
    }

    /** Alıcıya teslim edildi: {@code DELIVERED}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delivered(long sellerOrderId, Instant now) {
        apply(sellerOrderId, SellerOrderTransition.DELIVER, now);
    }

    /** Kargo teslim edemedi: {@code DELIVERY_FAILED}; alıcıya ve satıcıya e-posta, admin karar verir (D7). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliveryFailed(long sellerOrderId, Instant now) {
        apply(sellerOrderId, SellerOrderTransition.FAIL_DELIVERY, now)
                .ifPresent(notifier::sellerOrderDeliveryFailed);
    }

    /** @return geçiş uygulandıysa satıcı siparişi; izinli değilse boş */
    private Optional<SellerOrder> apply(long sellerOrderId, SellerOrderTransition transition, Instant now) {
        var sellerOrder = sellerOrders.findByIdForUpdate(sellerOrderId)
                .orElseThrow(() -> new IllegalStateException("Gönderinin satıcı siparişi yok: " + sellerOrderId));
        if (!sellerOrder.allows(transition)) {
            log.info("Gönderi bildirimi satıcı siparişine uygulanmadı: satıcı siparişi {}, durum {}, geçiş {}",
                    sellerOrderId, sellerOrder.getStatus(), transition);
            return Optional.empty();
        }
        var previous = sellerOrder.getStatus();
        sellerOrder.apply(transition, now);
        statusChanges.save(OrderStatusChange.recordBySystem(sellerOrderId, previous, sellerOrder.getStatus(), now));
        return Optional.of(sellerOrder);
    }
}
