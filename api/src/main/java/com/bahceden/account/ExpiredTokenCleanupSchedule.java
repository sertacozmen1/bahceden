package com.bahceden.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Süresi geçmiş kimlik kayıtları temizliğinin zamanlaması
 * (add-stale-record-cleanup D2).
 *
 * <p>Ödenmemiş sipariş işiyle aynı ayarları kullanır ama ondan bağımsızdır.
 * {@code bahceden.cleanup.enabled} kapalıysa (testler) zamanlayıcı hiç kurulmaz.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bahceden.cleanup.enabled", havingValue = "true")
class ExpiredTokenCleanupSchedule {

    private final ExpiredTokenCleanup cleanup;

    ExpiredTokenCleanupSchedule(ExpiredTokenCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(initialDelayString = "${bahceden.cleanup.interval}", fixedDelayString = "${bahceden.cleanup.interval}")
    void clean() {
        cleanup.cleanOnce();
    }
}
