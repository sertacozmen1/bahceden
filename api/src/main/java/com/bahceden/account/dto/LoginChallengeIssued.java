package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Girişin ikinci adım beklediği bilgisi (ADR-017, design.md D8).
 *
 * <p>Yalnızca ikinci faktör anahtarı açık hesaplarda döner. Token içermez:
 * oturum, e-postayla gönderilen kod doğrulandığında açılır.
 *
 * @param message   kullanıcıya gösterilebilir bilgilendirme, isteğin dilinde (ADR-031)
 * @param expiresAt kodun bitiş anı (UTC)
 */
@Schema(name = "LoginChallengeIssued", description = "İkinci adım bekleniyor: e-postayla kod gönderildi")
public record LoginChallengeIssued(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kullanıcıya gösterilebilir bilgilendirme; isteğin diliyle (Accept-Language) döner",
                example = "E-posta adresinize bir giriş kodu gönderdik.")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kodun bitiş anı (UTC)", example = "2026-10-07T12:10:00Z")
        Instant expiresAt) {
}
