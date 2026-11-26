package com.bahceden.common.ratelimit;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Sabit pencere hız sınırı (ADR-022 semantiği), sayaçlar Redis'te (ADR-108).
 *
 * <p>Sayaç bütün uygulama örneklerinin ortak kullandığı Redis'te tutulur: sınır
 * örnekler arasında toplamda uygulanır ve yeniden başlatma sayaçları sıfırlamaz.
 *
 * <p><strong>Fail-open:</strong> Redis'e ulaşılamazsa istek bu yüzden reddedilmez;
 * aynı politika süreç içi yedek sayaçla uygulanır ({@link InMemoryWindowCounter})
 * ve dakikada en çok bir {@code WARN} yazılır. Redis kesintisi giriş ve kayıt
 * uçlarını kapatmaz; kesinti boyunca koruma örnek başınadır.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /** Yedeğe düşüş uyarısının en sık yazılma aralığı; kesintide log dolmaz. */
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);

    private final Clock clock;
    private final WindowCounter shared;
    private final WindowCounter fallback = new InMemoryWindowCounter();
    private final AtomicReference<Instant> lastWarning = new AtomicReference<>(Instant.MIN);

    RateLimiter(Clock clock, StringRedisTemplate redis) {
        this.clock = clock;
        this.shared = new RedisWindowCounter(redis);
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
        long count;
        try {
            count = shared.increment(key, policy.window(), now);
        } catch (DataAccessException unavailable) {
            warnFallback(now, unavailable);
            count = fallback.increment(key, policy.window(), now);
        }
        if (count > policy.limit()) {
            throw new ApiException(ApiErrorCode.RATE_LIMITED, "error.rateLimited");
        }
    }

    private void warnFallback(Instant now, DataAccessException cause) {
        var last = lastWarning.get();
        if (now.isBefore(last.plus(WARN_INTERVAL)) && !now.isBefore(last)) {
            return;
        }
        if (lastWarning.compareAndSet(last, now)) {
            log.warn("Hız sınırı Redis'e ulaşamadı; süreç içi sayaçla devam ediliyor: {}",
                    cause.getClass().getSimpleName());
        }
    }
}
