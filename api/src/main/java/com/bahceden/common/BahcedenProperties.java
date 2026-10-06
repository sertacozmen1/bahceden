package com.bahceden.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
 */
@Validated
@ConfigurationProperties(prefix = "bahceden")
public record BahcedenProperties(
        @NotBlank String webOrigin,
        @NotBlank @Email String mailFrom) {
}
