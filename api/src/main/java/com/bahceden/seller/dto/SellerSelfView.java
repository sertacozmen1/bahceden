package com.bahceden.seller.dto;

import com.bahceden.seller.domain.SellerDocumentType;
import com.bahceden.seller.domain.SellerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Oturum sahibinin kendi satıcı kaydı (seller-onboarding spec'i).
 *
 * <p>Telefon burada vardır çünkü kaydın sahibi kendi verisini görür; herkese
 * açık şemalarda (vitrin) yoktur (K10). Hesap ve satıcı kimliği dönmez: kayıt
 * oturumdan bulunur, arayüzün kimliğe ihtiyacı yok.
 *
 * @param slug            vitrin adresinde kullanılacak kalıcı ad
 * @param displayName     vitrinde görünecek üretici adı
 * @param city            şehir
 * @param phone           E.164 biçiminde iletişim telefonu
 * @param status          kaydın durumu
 * @param statusReason    son ret veya askı gerekçesi; yoksa {@code null}
 * @param documentType    güncel belgenin türü
 * @param statusChangedAt durumun son değiştiği an (UTC)
 */
@Schema(name = "SellerSelfView", description = "Oturum sahibinin satıcı kaydı")
public record SellerSelfView(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vitrin adresinde kullanılacak kalıcı ad", example = "ornek-bahce")
        String slug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vitrinde görünecek üretici adı", example = "Örnek Bahçe")
        String displayName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Şehir", example = "Manisa")
        String city,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İletişim telefonu, E.164 biçiminde", example = "+905550000000")
        String phone,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kaydın durumu", example = "PENDING")
        SellerStatus status,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Son ret veya askı gerekçesi; satıcıya gösterilir",
                example = "Belgedeki ad ile beyan edilen ad eşleşmiyor.")
        String statusReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Güncel belgenin türü",
                example = "CKS_CERTIFICATE")
        SellerDocumentType documentType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Durumun son değiştiği an (UTC, ISO-8601)", example = "2026-10-08T09:30:00Z")
        Instant statusChangedAt) {
}
