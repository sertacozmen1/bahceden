package com.bahceden.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Ödenmemiş sipariş işinin zamanlaması (add-stale-record-cleanup D1).
 *
 * <p>Talep süre işiyle aynı desen ({@code DefectClaimSweepSchedule}).
 * {@code bahceden.cleanup.enabled} kapalıysa (testler) zamanlayıcı hiç kurulmaz;
 * testler işi sabit saatle doğrudan çağırır.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bahceden.cleanup.enabled", havingValue = "true")
class PendingPaymentSweepSchedule {

    private final PendingPaymentSweep sweep;

    PendingPaymentSweepSchedule(PendingPaymentSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(initialDelayString = "${bahceden.cleanup.interval}", fixedDelayString = "${bahceden.cleanup.interval}")
    void sweep() {
        sweep.sweepOnce();
    }
}
