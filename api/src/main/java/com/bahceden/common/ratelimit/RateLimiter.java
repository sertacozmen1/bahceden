package com.bahceden.common.ratelimit;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Bellek içi sabit pencere sayacı (design.md D5).
 *
 * <p>Yeni paket yok: {@link ConcurrentHashMap} ve enjekte edilen {@link Clock}
 * yeterli; saat sabitlenebildiği için pencere davranışı testte doğrulanır.
 *
 * <p><strong>Bilinen sınırlama:</strong> sayaç süreç içindedir. İki örnek
 * çalışırsa sınır örnek başına uygulanır ve yeniden başlatma sayaçları sıfırlar
 * ({@code 00-current-state.md}). Tek sunuculu MVP için bilinçli seçim.
 */
@Component
public class RateLimiter {

    /** Bu sayıyı aşınca süresi geçmiş pencereler temizlenir; bellek sınırsız büyümez. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private static final String MESSAGE = "Çok fazla deneme yaptınız. Lütfen biraz sonra tekrar deneyin.";

    private record Window(Instant start, int count) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Bir istek sayar; pencere doluysa reddeder.
     *
     * @param policy  uç ailesinin politikası
     * @param keyPart istemciyi tanımlayan parçalar (IP, normalleştirilmiş e-posta, hesap kimliği)
     * @throws ApiException {@code RATE_LIMITED}
     */
    public void check(RateLimitPolicy policy, String... keyPart) {
        var now = clock.instant();
        var key = policy.name() + '|' + String.join("|", keyPart);
        var window = windows.compute(key, (ignored, current) ->
                current == null || expired(current, policy, now)
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (windows.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
        if (window.count() > policy.limit()) {
            throw new ApiException(ApiErrorCode.RATE_LIMITED, MESSAGE);
        }
    }

    private static boolean expired(Window window, RateLimitPolicy policy, Instant now) {
        return !now.isBefore(window.start().plus(policy.window()));
    }

    /** En uzun pencereden eski girişler artık hiçbir politikada sayılmaz. */
    private void sweep(Instant now) {
        var oldestRelevant = now.minus(longestWindow());
        windows.values().removeIf(window -> window.start().isBefore(oldestRelevant));
    }

    private static Duration longestWindow() {
        var longest = Duration.ZERO;
        for (var policy : RateLimitPolicy.values()) {
            if (policy.window().compareTo(longest) > 0) {
                longest = policy.window();
            }
        }
        return longest;
    }
}
