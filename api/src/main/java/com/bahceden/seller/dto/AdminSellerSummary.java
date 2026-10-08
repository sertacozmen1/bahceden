package com.bahceden.seller.dto;

import com.bahceden.seller.domain.SellerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Admin listesindeki satıcı satırı (seller-onboarding spec'i "Admin
 * başvuruları karara bağlar"). Kişisel veri (telefon, e-posta) listede yoktur;
 * yalnızca detayda görünür.
 *
 * @param id              satıcı kaydının kimliği; detay ve eylem adreslerinde kullanılır
 * @param displayName     üretici adı
 * @param city            şehir
 * @param status          kaydın durumu
 * @param createdAt       başvurunun ilk yapıldığı an (UTC)
 * @param statusChangedAt durumun son değiştiği an (UTC)
 */
@Schema(name = "AdminSellerSummary", description = "Admin listesinde satıcı kaydı özeti")
public record AdminSellerSummary(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı kaydının kimliği", example = "42")
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretici adı", example = "Örnek Bahçe")
        String displayName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Şehir", example = "Manisa")
        String city,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kaydın durumu", example = "PENDING")
        SellerStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Başvurunun ilk yapıldığı an (UTC, ISO-8601)", example = "2026-10-08T09:30:00Z")
        Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Durumun son değiştiği an (UTC, ISO-8601); seed kayıtlarında boş olabilir",
                example = "2026-10-08T09:30:00Z")
        Instant statusChangedAt) {
}
