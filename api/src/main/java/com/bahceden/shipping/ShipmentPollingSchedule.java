package com.bahceden.shipping;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Gönderi durumu sorgusunun zamanlaması (add-shipments D5).
 *
 * <p>Spring'in kendi zamanlayıcısı kullanılır; kuyruk veya iş zamanlayıcı
 * kütüphanesi alınmaz (AGENTS.md §3, K7). {@code bahceden.shipping.polling-enabled}
 * kapalıysa (testler) zamanlayıcı hiç kurulmaz; testler sorguyu sabit saatle
 * doğrudan çağırır.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bahceden.shipping.polling-enabled", havingValue = "true")
class ShipmentPollingSchedule {

    private final ShipmentStatusPoller poller;

    ShipmentPollingSchedule(ShipmentStatusPoller poller) {
        this.poller = poller;
    }

    @Scheduled(initialDelayString = "${bahceden.shipping.poll-interval}",
            fixedDelayString = "${bahceden.shipping.poll-interval}")
    void poll() {
        poller.pollOnce();
    }
}
