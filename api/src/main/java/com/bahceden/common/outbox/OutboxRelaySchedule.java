package com.bahceden.common.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox relay'inin ve saklama temizliğinin zamanlaması (ADR-110).
 *
 * <p>Diğer zamanlanmış işlerle aynı desen ({@code PendingPaymentSweepSchedule}).
 * {@code bahceden.outbox.enabled} kapalıysa (testler) zamanlayıcı hiç kurulmaz;
 * testler turları doğrudan çağırır.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bahceden.outbox.enabled", havingValue = "true")
class OutboxRelaySchedule {

    private final OutboxRelay relay;

    OutboxRelaySchedule(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(initialDelayString = "${bahceden.outbox.interval}", fixedDelayString = "${bahceden.outbox.interval}")
    void relay() {
        relay.relayOnce();
    }

    @Scheduled(initialDelayString = "${bahceden.cleanup.interval}", fixedDelayString = "${bahceden.cleanup.interval}")
    void purge() {
        relay.purgePublished();
    }
}
