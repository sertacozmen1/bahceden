package com.bahceden.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Süreç içi sabit pencere sayacı (ADR-022): Redis'e ulaşılamadığında yedek (ADR-108).
 *
 * <p>Bu kod ADR-022'deki {@code RateLimiter} gövdesinin kendisidir, yeniden
 * yazılmadı (AGENTS.md K9). Sayaç örnek başınadır: yedek çalışırken sınır her
 * örnekte ayrı uygulanır.
 */
final class InMemoryWindowCounter implements WindowCounter {

    /** Bu sayıyı aşınca süresi geçmiş pencereler temizlenir; bellek sınırsız büyümez. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private record Window(Instant start, Duration length, long count) {

        boolean expired(Instant now) {
            return !now.isBefore(start.plus(length));
        }
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration window, Instant now) {
        var current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.expired(now)
                        ? new Window(now, window, 1)
                        : new Window(existing.start(), existing.length(), existing.count() + 1));
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(entry -> entry.expired(now));
        }
        return current.count();
    }
}
