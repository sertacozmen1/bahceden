package com.bahceden.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Uygulamaya özgü ayarlar.
 *
 * <p>Ayarlar yalnızca tip güvenli bağlama ile okunur; dağınık {@code @Value} ve
 * kodda sabit host/secret yasaktır (AGENTS.md §11.3, K10). {@code @Validated}
 * sayesinde eksik veya boş bir değerle uygulama <strong>başlamaz</strong>: yanlış
 * yapılandırma çalışma zamanında değil ayağa kalkarken görünür.
 *
 * @param webOrigin CORS için izin verilen tek kaynak ve kullanıcıya gönderilen
 *                  bağlantıların tabanı (doğrulama bağlantısı buradan kurulur).
 *                  Joker karakter kullanılmaz (AGENTS.md §11.6).
 * @param mailFrom  giden e-postaların gönderen adresi
 * @param jwt       erişim token'ı imza ayarları (ADR-008)
 */
@Validated
@ConfigurationProperties(prefix = "bahceden")
public record BahcedenProperties(
        @NotBlank String webOrigin,
        @NotBlank @Email String mailFrom,
        @NotNull @Valid Jwt jwt) {

    /**
     * Erişim token'ı imza ayarları.
     *
     * <p>Anahtar HMAC-SHA256 içindir ve en az 256 bit olmalıdır; daha kısa bir
     * anahtar kaba kuvvete açıktır. Kısa, Base64 olmayan veya yer tutucu bir
     * değerle uygulama <strong>açılmaz</strong> (K10 fail-fast). Hata mesajı
     * değerin kendisini içermez.
     *
     * @param secret Base64 kodlu imza anahtarı; yalnızca ortamdan gelir
     */
    public record Jwt(@NotBlank String secret) {

        /** HS256 için asgari anahtar uzunluğu: 32 bayt = 256 bit. */
        private static final int MIN_KEY_BYTES = 32;

        public Jwt {
            if (secret != null && !secret.isBlank()) {
                decode(secret);
            }
        }

        /** Çözülmüş anahtar baytları; her çağrıda yeni dizi döner. */
        public byte[] keyBytes() {
            return decode(secret);
        }

        private static byte[] decode(String secret) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(secret.trim());
            } catch (IllegalArgumentException cause) {
                throw new IllegalArgumentException("bahceden.jwt.secret Base64 kodlu olmalıdır");
            }
            if (bytes.length < MIN_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "bahceden.jwt.secret en az " + MIN_KEY_BYTES + " bayt (256 bit) olmalıdır");
            }
            return bytes;
        }
    }
}
