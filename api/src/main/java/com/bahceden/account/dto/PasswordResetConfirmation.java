package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Parola sıfırlama onayı: bağlantıdaki token ve yeni parola (ADR-035).
 *
 * <p>Parola kuralları kayıttakiyle aynıdır. Doğrulama hatası token'ı tüketmez:
 * kısıtlar service'e girmeden denetlenir.
 *
 * @param token    sıfırlama bağlantısındaki tek kullanımlık değer
 * @param password yeni düz parola; yalnızca hash'lenmek üzere taşınır
 */
@Schema(name = "PasswordResetConfirmation", description = "Parola sıfırlama onayı")
public record PasswordResetConfirmation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Sıfırlama bağlantısındaki tek kullanımlık token",
                example = "q3Jx0ornekSifirlamaTokeni9Zt")
        @NotBlank(message = "{validation.passwordResetToken.required}")
        @Size(max = 200, message = "{validation.passwordResetToken.max}")
        String token,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yeni parola; en az 10 karakter", example = "yeni-cok-gizli-parola")
        @NotBlank(message = "{validation.password.required}")
        @Size(min = 10, max = 200, message = "{validation.password.length}")
        String password) {
}
