package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * İkinci adımı açma veya kapatma isteği (add-two-factor-toggle D1, ADR-103).
 *
 * <p>Hesap gövdeden değil oturumdan gelir. Mevcut parola her iki yönde de
 * istenir: çalınmış bir oturum korumayı tek başına kaldıramaz. Parolanın
 * doğruluğu ve açmak için e-posta doğrulaması service'te denetlenir.
 *
 * @param enabled         ikinci adım açık mı olsun
 * @param currentPassword hesabın şu anki düz parolası; yalnızca karşılaştırılır
 */
@Schema(name = "TwoFactorUpdate", description = "İki adımlı girişi açma veya kapatma isteği")
public record TwoFactorUpdate(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İki adımlı giriş açık mı olsun", example = "true")
        @NotNull(message = "{validation.twoFactor.enabledRequired}")
        Boolean enabled,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hesabın şu anki parolası", example = "cok-gizli-parola")
        @NotBlank(message = "{validation.currentPassword.required}")
        @Size(max = 200, message = "{validation.currentPassword.invalid}")
        String currentPassword) {
}
