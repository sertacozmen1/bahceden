package com.bahceden.account.dto;

import com.bahceden.account.domain.AccountRole;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Oturum sahibinin kimliği (design.md D7).
 *
 * <p>Veritabanından taze okunur: roller token'daki iddialardan değil güncel
 * kayıttan gelir. Hesap kimliği dönmez; arayüzün ona ihtiyacı yok.
 *
 * @param email         hesabın e-posta adresi
 * @param roles         hesabın rolleri, alfabetik
 * @param emailVerified e-posta doğrulandı mı; giriş koşulu değil, bilgidir (ADR-017)
 * @param twoFactorEnabled iki adımlı giriş açık mı (ADR-103)
 */
@Schema(name = "AccountView", description = "Oturum sahibinin kimliği ve rolleri")
public record AccountView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E-posta adresi", example = "alici@ornek.test")
        String email,

        @ArraySchema(schema = @Schema(description = "Hesap rolü", example = "ROLE_CUSTOMER"),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hesabın rolleri"))
        List<AccountRole> roles,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E-posta adresi doğrulandı mı; giriş koşulu değildir", example = "false")
        boolean emailVerified,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İki adımlı giriş açık mı; açıksa her girişte e-postaya kod gider", example = "false")
        boolean twoFactorEnabled) {
}
