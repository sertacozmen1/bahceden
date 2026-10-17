package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Oturum açıkken parola değiştirme isteği (add-password-change D1).
 *
 * <p>Hesap gövdeden değil oturumdan gelir. Yeni parolanın kuralları kayıttakiyle
 * aynıdır; mevcut parolanın doğruluğu ve yeni parolanın ondan farklı olması
 * service'te denetlenir.
 *
 * @param currentPassword hesabın şu anki düz parolası; yalnızca karşılaştırılır
 * @param newPassword     yeni düz parola; yalnızca hash'lenmek üzere taşınır
 */
@Schema(name = "PasswordChangeRequest", description = "Oturum açıkken parola değiştirme isteği")
public record PasswordChangeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hesabın şu anki parolası", example = "eski-cok-gizli-parola")
        @NotBlank(message = "{validation.currentPassword.required}")
        @Size(max = 200, message = "{validation.currentPassword.invalid}")
        String currentPassword,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yeni parola; en az 10 karakter, mevcut paroladan farklı",
                example = "yeni-cok-gizli-parola")
        @NotBlank(message = "{validation.password.required}")
        @Size(min = 10, max = 200, message = "{validation.password.length}")
        String newPassword) {
}
