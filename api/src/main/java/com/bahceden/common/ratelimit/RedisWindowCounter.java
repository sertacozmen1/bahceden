package com.bahceden.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis'te paylaşılan sabit pencere sayacı (ADR-108).
 *
 * <p>Sayaç tek bir Lua betiğiyle atomik tutulur: pencerenin başlangıcı ve sayısı
 * aynı hash'te durur. Betik "şimdi"yi Redis saatinden değil çağırandan alır; bu
 * sayede pencere semantiği bellek içi sayaçla birebir aynıdır (ADR-022) ve
 * sabit saatle test edilir. {@code PEXPIRE} yalnızca çöp toplama içindir: süresi
 * geçmiş pencere Redis'ten kendiliğinden silinir.
 *
 * <p>Redis'e e-posta veya IP düz yazılmaz (K10): anahtar, parçaların SHA-256
 * özetidir. Redis hatası {@code DataAccessException} olarak çağırana çıkar.
 */
final class RedisWindowCounter implements WindowCounter {

    static final String KEY_PREFIX = "rl:";

    /**
     * KEYS[1] pencere anahtarı; ARGV[1] şimdi (ms), ARGV[2] pencere (ms).
     * Pencere yoksa veya dolmuşsa yeni pencere 1'den başlar; değilse sayaç artar.
     */
    private static final RedisScript<Long> SCRIPT = RedisScript.of("""
            local start = redis.call('HGET', KEYS[1], 's')
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            if (not start) or (now - tonumber(start) >= window) then
              redis.call('HSET', KEYS[1], 's', now, 'c', 1)
              redis.call('PEXPIRE', KEYS[1], window)
              return 1
            end
            return redis.call('HINCRBY', KEYS[1], 'c', 1)
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisWindowCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window, Instant now) {
        var count = redis.execute(SCRIPT, List.of(redisKey(key)),
                Long.toString(now.toEpochMilli()), Long.toString(window.toMillis()));
        return count == null ? 1 : count;
    }

    /** {@code policy|parça|parça} → {@code rl:POLICY:sha256(parçalar)}; politika adı okunur kalır. */
    static String redisKey(String key) {
        int separator = key.indexOf('|');
        var policy = separator < 0 ? key : key.substring(0, separator);
        var parts = separator < 0 ? "" : key.substring(separator + 1);
        return KEY_PREFIX + policy + ':' + sha256(parts);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 her JDK'da zorunludur (Java SE MessageDigest şartnamesi).
            throw new IllegalStateException(impossible);
        }
    }
}
