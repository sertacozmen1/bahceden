package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.order.domain.OrderStatus;
import com.bahceden.payment.PaymentProviderException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Ödeme dönüşü hiç gelmeyen siparişlerin işi (cart-and-checkout spec'i,
 * add-stale-record-cleanup D1).
 *
 * <p>Bir turda ödeme süresi tolerans kadar önce dolmuş, en eski siparişlerden
 * yapılandırılmış sayıda alınır. Her sipariş kendi transaction'ında kilitlenip
 * yeniden denetlenir ({@link CheckoutService#expireUnpaid(long)}): sağlayıcı
 * hatası o siparişi değiştirmez, sonraki turda yeniden denenir; diğer siparişler
 * işlenmeye devam eder.
 *
 * <p>Tek örnek varsayılır (AGENTS.md §3); iki örnek aynı siparişi işlese de
 * kilit ve yeniden okuma çift kapatmayı ve çift iadeyi önler.
 */
@Component
public class PendingPaymentSweep {

    private static final Logger log = LoggerFactory.getLogger(PendingPaymentSweep.class);

    private final OrderRepository orders;
    private final CheckoutService checkout;
    private final Duration grace;
    private final int batchSize;
    private final Clock clock;

    PendingPaymentSweep(OrderRepository orders, CheckoutService checkout, BahcedenProperties properties, Clock clock) {
        this.orders = orders;
        this.checkout = checkout;
        this.grace = properties.cleanup().pendingPaymentGrace();
        this.batchSize = properties.cleanup().pendingPaymentBatchSize();
        this.clock = clock;
    }

    /**
     * Tek tur.
     *
     * @return sonuçlandırılan sipariş sayısı
     */
    public int sweepOnce() {
        var ids = orders.findIdsExpiredBefore(OrderStatus.PENDING_PAYMENT, clock.instant().minus(grace),
                PageRequest.of(0, batchSize));
        int closed = 0;
        for (var id : ids) {
            try {
                var outcome = checkout.expireUnpaid(id);
                if (outcome.isPresent()) {
                    closed++;
                    log.info("Ödenmemiş sipariş kapatıldı: sipariş kimliği {}, {}", id, outcome.get());
                }
            } catch (PaymentProviderException cause) {
                log.warn("Ödenmemiş sipariş doğrulanamadı, sonraki turda yeniden denenecek: sipariş kimliği {}", id,
                        cause);
            }
        }
        return closed;
    }
}
