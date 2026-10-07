package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Oturum açma isteği.
 *
 * <p>Parola uzunluk kuralı burada <strong>uygulanmaz</strong>: kural kayıtta
 * denetlenir; girişte uygulamak, kural sıkılaştırıldığında eski hesapları
 * kilitlerdi. Üst sınırlar yalnızca istek boyutunu korur.
 *
 * @param email    hesabın e-posta adresi
 * @param password düz parola; yalnızca karşılaştırılır, saklanmaz ve loglanmaz
 */
@Schema(name = "LoginRequest", description = "Oturum açma isteği")
public record LoginRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E-posta adresi", example = "alici@ornek.test")
        @NotBlank(message = "E-posta adresi zorunludur")
        @Size(max = 320, message = "E-posta adresi en çok 320 karakter olabilir")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Parola", example = "cok-gizli-parola")
        @NotBlank(message = "Parola zorunludur")
        @Size(max = 200, message = "Parola en çok 200 karakter olabilir")
        String password) {
}
