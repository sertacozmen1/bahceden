package com.bahceden.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * İleri sarılabilen saat (AGENTS.md §11.3: "şimdi" enjekte edilir).
 *
 * <p>Süre davranışları (kodun, yenileme token'ının, erişim token'ının dolması;
 * hız sınırı penceresinin kayması) gerçek bekleme olmadan test edilir.
 * Uygulamanın tüm bileşenleri, JWT doğrulayıcısı dahil, aynı saati görür.
 *
 * <p>Hız sınırı sayaçları paylaşılan test bağlamında testler arasında yaşar;
 * testler {@link #advance(Duration)} ile pencereleri geride bırakarak birbirinden
 * bağımsız kalır.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.SECONDS));

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    /** Uygulamanın {@code Clock} bean'inin yerine geçer. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
