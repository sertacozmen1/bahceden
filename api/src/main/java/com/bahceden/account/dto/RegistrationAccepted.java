package com.bahceden.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kayıt isteğinin yanıtı.
 *
 * <p>Bu yanıt <strong>her durumda aynıdır</strong> (design.md D4): e-posta
 * sistemde olsa da olmasa da aynı mesaj döner. Aksi halde kayıt ucu, bir
 * adresin kayıtlı olup olmadığını öğrenmenin en kolay yolu olurdu.
 *
 * <p>Hesap kimliği veya durumu <strong>dönmez</strong>: döndürülen her ek alan
 * bu bilgiyi geri sızdırma riski taşır.
 *
 * @param message kullanıcıya gösterilebilir Türkçe bilgilendirme
 */
@Schema(name = "RegistrationAccepted", description = "Kayıt isteği alındı bilgisi")
public record RegistrationAccepted(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kullanıcıya gösterilebilir bilgilendirme",
                example = "E-posta adresinize bir bağlantı gönderdik.") String message) {

    /** Tüm kayıt sonuçları için tek mesaj (D4). */
    public static RegistrationAccepted standard() {
        return new RegistrationAccepted(
                "E-posta adresinize bir bağlantı gönderdik. Hesabınızla hemen giriş yapabilirsiniz.");
    }
}
