package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Girişin ikinci adım beklediği bilgisi (ADR-017, design.md D8).
 *
 * <p>Yalnızca ikinci faktör anahtarı açık hesaplarda döner. Token içermez:
 * oturum, e-postayla gönderilen kod doğrulandığında açılır.
 *
 * @param message   kullanıcıya gösterilebilir Türkçe bilgilendirme
 * @param expiresAt kodun bitiş anı (UTC)
 */
@Schema(name = "LoginChallengeIssued", description = "İkinci adım bekleniyor: e-postayla kod gönderildi")
public record LoginChallengeIssued(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kullanıcıya gösterilebilir bilgilendirme",
                example = "E-posta adresinize bir giriş kodu gönderdik.")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kodun bitiş anı (UTC)", example = "2026-10-07T12:10:00Z")
        Instant expiresAt) {

    public static LoginChallengeIssued until(Instant expiresAt) {
        return new LoginChallengeIssued("E-posta adresinize bir giriş kodu gönderdik.", expiresAt);
    }
}
