package com.bahceden.seller.dto;

import com.bahceden.seller.domain.SellerDocumentType;
import com.bahceden.seller.domain.SellerStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Admin'in karar vermek için gördüğü satıcı kaydı: beyan, iletişim, belge türü
 * ve geçmiş (seller-onboarding spec'i "Admin başvuruları karara bağlar").
 *
 * <p>Telefon ve hesap e-postası kişisel veridir (K10); yalnızca bu admin
 * şemasında ve sahibinin kendi görünümünde bulunur. Seed'den gelen satıcıların
 * hesabı, telefonu ve belgesi yoktur; o alanlar boş döner.
 *
 * @param id              satıcı kaydının kimliği
 * @param slug            vitrin adresindeki kalıcı ad
 * @param displayName     üretici adı
 * @param city            şehir
 * @param phone           E.164 telefon; seed kayıtlarında boş
 * @param accountEmail    kaydın sahibi olan hesabın e-postası; seed kayıtlarında boş
 * @param status          kaydın durumu
 * @param statusReason    son ret veya askı gerekçesi
 * @param documentType    güncel belgenin türü; belgesiz seed kayıtlarında boş
 * @param createdAt       başvurunun ilk yapıldığı an (UTC)
 * @param statusChangedAt durumun son değiştiği an (UTC)
 * @param history         durum geçişleri, eskiden yeniye
 */
@Schema(name = "AdminSellerDetail", description = "Admin için satıcı kaydı ayrıntısı ve geçmişi")
public record AdminSellerDetail(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı kaydının kimliği", example = "42")
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vitrin adresindeki kalıcı ad", example = "ornek-bahce")
        String slug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretici adı", example = "Örnek Bahçe")
        String displayName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Şehir", example = "Manisa")
        String city,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "İletişim telefonu, E.164; seed kayıtlarında boş", example = "+905550000000")
        String phone,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kaydın sahibi olan hesabın e-postası; seed kayıtlarında boş",
                example = "uretici@ornek.test")
        String accountEmail,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kaydın durumu", example = "PENDING")
        SellerStatus status,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Son ret veya askı gerekçesi", example = "Belgedeki ad okunmuyor.")
        String statusReason,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Güncel belgenin türü; belgesiz seed kayıtlarında boş", example = "CKS_CERTIFICATE")
        SellerDocumentType documentType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Başvurunun ilk yapıldığı an (UTC, ISO-8601)", example = "2026-10-08T09:30:00Z")
        Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Durumun son değiştiği an (UTC, ISO-8601); seed kayıtlarında boş olabilir",
                example = "2026-10-08T09:30:00Z")
        Instant statusChangedAt,

        @ArraySchema(schema = @Schema(implementation = SellerStatusChangeView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Durum geçişleri, eskiden yeniye"))
        List<SellerStatusChangeView> history) {
}
