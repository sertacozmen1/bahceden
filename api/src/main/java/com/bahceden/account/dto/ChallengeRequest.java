package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Girişin ikinci adımı: e-postayla gönderilen kod (design.md D8).
 *
 * <p>Kod tek başına aranmaz; e-posta, kodun hangi hesabın girişimine karşı
 * doğrulanacağını belirler.
 *
 * @param email birinci adımda kullanılan e-posta adresi
 * @param code  e-postadaki altı haneli kod
 */
@Schema(name = "ChallengeRequest", description = "İkinci faktör kodu")
public record ChallengeRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Birinci adımda kullanılan e-posta adresi", example = "alici@ornek.test")
        @NotBlank(message = "E-posta adresi zorunludur")
        @Size(max = 320, message = "E-posta adresi en çok 320 karakter olabilir")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E-postadaki altı haneli kod", example = "042917")
        @NotBlank(message = "Kod zorunludur")
        @Pattern(regexp = "\\d{6}", message = "Kod altı haneli olmalıdır")
        String code) {
}
