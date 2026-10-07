package com.bahceden.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Sabit pencere hız sınırı (design.md D5). Saat sabittir: pencere davranışı
 * gerçek bekleme olmadan doğrulanır.
 */
@Tag("critical")
@DisplayName("RateLimiter: sabit pencere")
class RateLimiterTest {

    private static final RateLimitPolicy POLICY = RateLimitPolicy.LOGIN;

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    @DisplayName("Pencere içinde N istek geçer, N+1. istek RATE_LIMITED alır")
    void sinir_asilinca_reddedilir() {
        for (int i = 0; i < POLICY.limit(); i++) {
            limiter.check(POLICY, "10.0.0.1", "alici@ornek.test");
        }

        assertThatThrownBy(() -> limiter.check(POLICY, "10.0.0.1", "alici@ornek.test"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.RATE_LIMITED));
    }

    @Test
    @DisplayName("Pencere kayınca istekler yeniden kabul edilir")
    void pencere_kayinca_yeniden_acilir() {
        fillWindow("10.0.0.1");

        clock.advance(POLICY.window().minusSeconds(1));
        assertThatThrownBy(() -> limiter.check(POLICY, "10.0.0.1")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.check(POLICY, "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Anahtarlar birbirinden bağımsızdır: bir istemcinin sınırı diğerini etkilemez")
    void anahtarlar_bagimsizdir() {
        fillWindow("10.0.0.1");

        assertThatCode(() -> limiter.check(POLICY, "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(RateLimitPolicy.REFRESH, "10.0.0.1")).doesNotThrowAnyException();
    }

    private void fillWindow(String key) {
        for (int i = 0; i < POLICY.limit(); i++) {
            limiter.check(POLICY, key);
        }
    }
}
