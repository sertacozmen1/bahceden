package com.bahceden.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zaman kaynağı.
 *
 * <p>"Şimdi" doğrudan {@code Instant.now()} ile değil, enjekte edilen bir
 * {@link Clock} ile alınır (AGENTS.md §11.3): zamana bağlı davranış ancak saat
 * sabitlenebiliyorsa test edilebilir. Saklama her zaman UTC'dir; kullanıcıya
 * gösterim zaman dilimi {@code web} tarafının işidir.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
