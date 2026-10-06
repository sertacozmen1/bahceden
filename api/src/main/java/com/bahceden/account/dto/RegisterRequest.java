package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Hesap açma isteği.
 *
 * <p>Doğrulama kısıtları sözleşmeye yansır (AGENTS.md K2): istemci aynı
 * kuralları erken geri bildirim için kullanabilir ama otorite sunucudur.
 *
 * @param email    kullanıcının e-posta adresi; hesabın kimliği budur
 * @param password düz parola; yalnızca hash'lenmek üzere taşınır, saklanmaz
 */
@Schema(name = "RegisterRequest", description = "Hesap açma isteği")
public record RegisterRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E-posta adresi", example = "alici@ornek.test")
        @NotBlank(message = "E-posta adresi zorunludur")
        @Email(message = "Geçerli bir e-posta adresi girin")
        @Size(max = 320, message = "E-posta adresi en çok 320 karakter olabilir")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Parola; en az 10 karakter", example = "cok-gizli-parola")
        @NotBlank(message = "Parola zorunludur")
        @Size(min = 10, max = 200, message = "Parola en az 10, en çok 200 karakter olmalıdır")
        String password) {
}
