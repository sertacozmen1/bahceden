package com.bahceden.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.support.MutableClock;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sabit pencere hız sınırı, sayaçlar Redis'te (ADR-022 semantiği, ADR-108).
 *
 * <p>Gerçek Redis'e karşı koşar (Testcontainers); Lua betiği ve anahtar biçimi
 * ancak böyle doğrulanır. Saat sabittir: pencere davranışı gerçek bekleme
 * olmadan doğrulanır, çünkü betik "şimdi"yi uygulamanın saatinden alır.
 */
@Tag("critical")
@DisplayName("RateLimiter: Redis'te paylaşılan sabit pencere")
class RateLimiterTest {

    private static final RateLimitPolicy POLICY = RateLimitPolicy.LOGIN;
    private static final String EMAIL = "alici@ornek.test";
    private static final String IP = "10.0.0.1";

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);
    private static final List<LettuceConnectionFactory> FACTORIES = new ArrayList<>();
    private static StringRedisTemplate redis;

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock, redis);

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        redis = template(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopRedis() {
        FACTORIES.forEach(LettuceConnectionFactory::destroy);
        REDIS.stop();
    }

    @BeforeEach
    void emptyRedis() {
        redis.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("Pencere içinde N istek geçer, N+1. istek RATE_LIMITED alır")
    void sinir_asilinca_reddedilir() {
        for (int i = 0; i < POLICY.limit(); i++) {
            limiter.check(POLICY, IP, EMAIL);
        }

        assertThatThrownBy(() -> limiter.check(POLICY, IP, EMAIL))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.RATE_LIMITED));
    }

    @Test
    @DisplayName("Pencere kayınca istekler yeniden kabul edilir")
    void pencere_kayinca_yeniden_acilir() {
        fillWindow(limiter, IP);

        clock.advance(POLICY.window().minusSeconds(1));
        assertThatThrownBy(() -> limiter.check(POLICY, IP)).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.check(POLICY, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Anahtarlar birbirinden bağımsızdır: bir istemcinin sınırı diğerini etkilemez")
    void anahtarlar_bagimsizdir() {
        fillWindow(limiter, IP);

        assertThatCode(() -> limiter.check(POLICY, "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(RateLimitPolicy.REFRESH, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("İki uygulama örneği aynı sınırı paylaşır: istekler dağılsa da toplam sayılır (spec: iki örnek)")
    void iki_ornek_ayni_siniri_paylasir() {
        var other = new RateLimiter(clock, redis);
        for (int i = 0; i < POLICY.limit(); i++) {
            (i % 2 == 0 ? limiter : other).check(POLICY, IP, EMAIL);
        }

        assertThatThrownBy(() -> limiter.check(POLICY, IP, EMAIL)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> other.check(POLICY, IP, EMAIL)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Yeniden başlatma sayacı sıfırlamaz: yeni örnek dolu pencereyi görür (spec: yeniden başlatma)")
    void yeniden_baslatma_sayaci_sifirlamaz() {
        fillWindow(limiter, IP);

        var restarted = new RateLimiter(clock, redis);

        assertThatThrownBy(() -> restarted.check(POLICY, IP)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Redis anahtarında e-posta ve IP düz yazılmaz; politika adı okunur kalır (K10)")
    void anahtarda_kisisel_veri_yok() {
        limiter.check(POLICY, IP, EMAIL);

        var keys = new ArrayList<String>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(RedisWindowCounter.KEY_PREFIX + "*").build())) {
            cursor.forEachRemaining(keys::add);
        }

        assertThat(keys).singleElement().satisfies(key -> {
            assertThat(key).startsWith("rl:LOGIN:").doesNotContain(EMAIL).doesNotContain(IP);
            assertThat(key.substring("rl:LOGIN:".length())).matches("[0-9a-f]{64}");
        });
    }

    @Test
    @DisplayName("Redis erişilemezse istek reddedilmez; sınır süreç içi yedekle örnek başına uygulanır (spec: ortak depo erişilemez)")
    void redis_yoksa_yedek_sayac_calisir() {
        var unreachable = new RateLimiter(clock, template("localhost", 1));

        for (int i = 0; i < POLICY.limit(); i++) {
            assertThatCode(() -> unreachable.check(POLICY, IP)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> unreachable.check(POLICY, IP))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.RATE_LIMITED));
    }

    private static void fillWindow(RateLimiter target, String key) {
        for (int i = 0; i < POLICY.limit(); i++) {
            target.check(POLICY, key);
        }
    }

    /** Kısa zaman aşımlı istemci: erişilemeyen Redis testi uzun beklemez. */
    private static StringRedisTemplate template(String host, int port) {
        var client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build())
                        .build())
                .build();
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
        factory.afterPropertiesSet();
        factory.start();
        FACTORIES.add(factory);
        return new StringRedisTemplate(factory);
    }
}
