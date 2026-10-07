package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * E-posta doğrulama isteği: e-postadaki bağlantıdan gelen token.
 *
 * @param token doğrulama bağlantısındaki tek kullanımlık değer
 */
@Schema(name = "VerifyEmailRequest", description = "E-posta doğrulama isteği")
public record VerifyEmailRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Doğrulama bağlantısındaki tek kullanımlık token",
                example = "q3Jx0ornekDogrulamaTokeni9Zt")
        @NotBlank(message = "{validation.verificationToken.required}")
        @Size(max = 200, message = "{validation.verificationToken.max}")
        String token) {
}
