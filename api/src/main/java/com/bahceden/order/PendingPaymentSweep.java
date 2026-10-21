package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.order.domain.OrderStatus;
import com.bahceden.payment.PaymentProviderException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Ödeme dönüşü hiç gelmeyen siparişlerin işi (cart-and-checkout spec'i,
 * add-stale-record-cleanup D1).
 *
 * <p>Bir tur, ödeme süresi tolerans kadar önce dolmuş bütün siparişleri en
 * eskiden başlayarak yapılandırılmış boyutta sayfalarla dolaşır; sonraki sayfa
 * son görülen siparişten sonrasıyla okunur (ADR-100). Her sipariş kendi
 * transaction'ında kilitlenip yeniden denetlenir
 * ({@link CheckoutService#expireUnpaid(long)}): sağlayıcı hatası o siparişi
 * değiştirmez, sonraki turda yeniden denenir ve arkadaki siparişleri bekletmez.
 * Bir turda her sipariş en çok bir kez denenir.
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
        var cutoff = clock.instant().minus(grace);
        var page = PageRequest.of(0, batchSize);
        int closed = 0;
        int failed = 0;
        SweepCursor last = null;
        List<SweepCursor> batch;
        do {
            batch = last == null
                    ? orders.findExpiredBefore(OrderStatus.PENDING_PAYMENT, cutoff, page)
                    : orders.findExpiredBeforeAfter(OrderStatus.PENDING_PAYMENT, cutoff, last.dueAt(), last.id(), page);
            for (var cursor : batch) {
                try {
                    var outcome = checkout.expireUnpaid(cursor.id());
                    if (outcome.isPresent()) {
                        closed++;
                        log.info("Ödenmemiş sipariş kapatıldı: sipariş kimliği {}, {}", cursor.id(), outcome.get());
                    }
                } catch (PaymentProviderException cause) {
                    failed++;
                    log.warn("Ödenmemiş sipariş doğrulanamadı, sonraki turda yeniden denenecek: sipariş kimliği {}",
                            cursor.id(), cause);
                }
                last = cursor;
            }
        } while (batch.size() == batchSize);
        if (failed > 0) {
            log.warn("Ödenmemiş sipariş turu: {} kapatıldı, {} hata", closed, failed);
        }
        return closed;
    }
}
