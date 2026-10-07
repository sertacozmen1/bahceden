package com.bahceden.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * İmza anahtarı açılışta doğrulanır: yer tutucu, kısa veya Base64 olmayan bir
 * değerle uygulama başlamaz (AGENTS.md K10, ADR-008). Hata mesajı değerin
 * kendisini içermez.
 */
@Tag("critical")
@DisplayName("bahceden.jwt.secret açılış doğrulaması")
class JwtSecretValidationTest {

    @Test
    @DisplayName(".env.example yer tutucusu kabul edilmez")
    void yer_tutucu_reddedilir() {
        assertThatThrownBy(() -> new BahcedenProperties.Jwt("degistirin-bu-degeri"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("degistirin-bu-degeri");
    }

    @Test
    @DisplayName("256 bitten kısa anahtar kabul edilmez")
    void kisa_anahtar_reddedilir() {
        var shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> new BahcedenProperties.Jwt(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bit");
    }

    @Test
    @DisplayName("256 bitlik Base64 anahtar kabul edilir")
    void yeterli_anahtar_kabul_edilir() {
        var key = "otuz-iki-bayttan-uzun-bir-test-anahtari".getBytes(StandardCharsets.UTF_8);

        assertThat(new BahcedenProperties.Jwt(Base64.getEncoder().encodeToString(key)).keyBytes()).isEqualTo(key);
    }
}
