package com.bahceden.common.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Sabit pencere sayacı (ADR-022 semantiği, ADR-108).
 *
 * <p>Pencere anahtarın ilk isteğiyle başlar ve {@code window} kadar sürer; süre
 * dolduktan sonraki ilk istek yeni pencereyi 1'den başlatır. "Şimdi" çağırandan
 * gelir (enjekte edilen {@code Clock}); böylece iki uygulama da aynı saatle,
 * gerçek bekleme olmadan test edilir.
 */
interface WindowCounter {

    /**
     * Anahtarın penceresinde bir istek sayar.
     *
     * @return bu istek dahil penceredeki istek sayısı
     */
    long increment(String key, Duration window, Instant now);
}
