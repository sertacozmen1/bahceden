package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Açılan oturumun token'ları (ADR-008).
 *
 * <p>Bu yanıtı yalnızca {@code web}'in sunucu tarafı (BFF) görür ve token'ları
 * {@code HttpOnly} çerezlere yazar; tarayıcı JavaScript'ine verilmez (design.md
 * D6). Bitiş anları çerez ömrünü ayarlamak içindir.
 *
 * @param accessToken           kısa ömürlü JWT erişim token'ı
 * @param accessTokenExpiresAt  erişim token'ının bitiş anı (UTC)
 * @param refreshToken          opak, tek kullanımlık yenileme token'ı
 * @param refreshTokenExpiresAt yenileme token'ının bitiş anı (UTC)
 */
@Schema(name = "SessionTokens", description = "Açılan oturumun erişim ve yenileme token'ları")
public record SessionTokens(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "JWT erişim token'ı; `Authorization: Bearer` başlığında gönderilir, 15 dakika geçerlidir",
                example = "eyJhbGciOiJIUzI1NiJ9.ornek.imza")
        String accessToken,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Erişim token'ının bitiş anı (UTC)", example = "2026-10-07T12:15:00Z")
        Instant accessTokenExpiresAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Opak yenileme token'ı; tek kullanımlıktır",
                example = "q3Jx0ornekYenilemeTokeni9Zt")
        String refreshToken,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yenileme token'ının bitiş anı (UTC)", example = "2026-11-06T12:00:00Z")
        Instant refreshTokenExpiresAt) {
}
