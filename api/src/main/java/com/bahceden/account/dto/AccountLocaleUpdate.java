package com.bahceden.account.dto;

import com.bahceden.common.i18n.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Oturum sahibinin dilini değiştirme isteği (add-account-locale-update D1).
 *
 * <p>Değer açıkça gövdede gelir, {@code Accept-Language}'den okunmaz: başlık
 * yanıt mesajlarının dilini taşır, hesabın e-posta dilini değil. Enum, tanınmayan
 * bir değerin sessizce varsayılana düşmesini engeller; okunamayan değer 400'dür.
 *
 * @param locale hesabın yeni dili; e-postalar bu dilde gider (ADR-032)
 */
@Schema(name = "AccountLocaleUpdate", description = "Hesabın e-posta dilini değiştirme isteği")
public record AccountLocaleUpdate(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hesabın yeni dili; hesaba giden e-postalar bu dilde gönderilir", example = "EN")
        @NotNull(message = "{validation.locale.required}")
        SupportedLocale locale) {
}
