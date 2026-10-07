package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yenileme token'ı taşıyan istek: oturum yenileme ve çıkış aynı gövdeyi kullanır.
 *
 * <p>Token'ı yalnızca {@code web}'in sunucu tarafı gönderir; tarayıcıda
 * {@code HttpOnly} çerezde durur ve betikle okunamaz (design.md D6).
 *
 * @param refreshToken girişte veya son yenilemede verilen opak token
 */
@Schema(name = "RefreshTokenRequest", description = "Yenileme token'ı")
public record RefreshTokenRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Opak yenileme token'ı", example = "q3Jx0ornekYenilemeTokeni9Zt")
        @NotBlank(message = "Yenileme token'ı zorunludur")
        @Size(max = 200, message = "Yenileme token'ı en çok 200 karakter olabilir")
        String refreshToken) {
}
