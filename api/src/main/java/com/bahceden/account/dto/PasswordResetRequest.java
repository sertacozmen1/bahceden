package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Parola sıfırlama talebi (ADR-034).
 *
 * <p>Kısıtlar kayıt isteğindeki e-posta kısıtlarıyla aynıdır. Yanıt adresin
 * kayıtlı olup olmadığını açığa çıkarmaz.
 *
 * @param email sıfırlama bağlantısının gönderileceği hesabın e-posta adresi
 */
@Schema(name = "PasswordResetRequest", description = "Parola sıfırlama talebi")
public record PasswordResetRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hesabın e-posta adresi", example = "alici@ornek.test")
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.format}")
        @Size(max = 320, message = "{validation.email.max}")
        String email) {
}
