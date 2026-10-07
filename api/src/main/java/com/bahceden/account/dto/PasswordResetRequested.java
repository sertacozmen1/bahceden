package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Parola sıfırlama talebinin yanıtı.
 *
 * <p>Bu yanıt <strong>her durumda aynıdır</strong> (ADR-034): adres kayıtlı
 * olsa da olmasa da aynı mesaj döner.
 *
 * @param message kullanıcıya gösterilebilir bilgilendirme, isteğin dilinde (ADR-031)
 */
@Schema(name = "PasswordResetRequested", description = "Parola sıfırlama talebi alındı bilgisi")
public record PasswordResetRequested(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kullanıcıya gösterilebilir bilgilendirme; isteğin diliyle (Accept-Language) döner",
                example = "Bu adrese kayıtlı bir hesap varsa parola sıfırlama bağlantısı gönderdik.") String message) {
}
