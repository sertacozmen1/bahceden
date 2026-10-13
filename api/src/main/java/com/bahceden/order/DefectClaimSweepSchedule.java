package com.bahceden.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Talep süre işinin zamanlaması (ADR-083).
 *
 * <p>Spring'in kendi zamanlayıcısı kullanılır; gönderi sorgusuyla aynı desen
 * ({@code ShipmentPollingSchedule}). {@code bahceden.claims.sweep-enabled}
 * kapalıysa (testler) zamanlayıcı hiç kurulmaz; testler işi sabit saatle
 * doğrudan çağırır.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bahceden.claims.sweep-enabled", havingValue = "true")
class DefectClaimSweepSchedule {

    private final DefectClaimSweep sweep;

    DefectClaimSweepSchedule(DefectClaimSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(initialDelayString = "${bahceden.claims.sweep-interval}",
            fixedDelayString = "${bahceden.claims.sweep-interval}")
    void sweep() {
        sweep.sweepOnce();
    }
}
