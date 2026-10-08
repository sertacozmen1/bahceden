package com.bahceden.catalog.dto;

import com.bahceden.catalog.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Admin ilan listesindeki satır (product-listings spec'i "Admin ilanı kaldırır
 * ve yeniden açar", add-product-listings D7). Admin ilanı alıcının gördüğü
 * gibi vitrin sayfasında inceler; satırda karar için gereken özet durur.
 *
 * @param id           ilanın kimliği; eylem adreslerinde kullanılır
 * @param slug         vitrin adresindeki kalıcı ad
 * @param name         ilan adı
 * @param status       ilanın durumu
 * @param statusReason kaldırma gerekçesi
 * @param seller       üretici
 * @param coverPhoto   kapak fotoğrafı; yoksa boş
 * @param updatedAt    son değişiklik anı
 */
@Schema(name = "AdminProductSummary", description = "Admin ilan listesinde ilan özeti")
public record AdminProductSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlanın kimliği", example = "42")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Vitrin adresindeki kalıcı ad", example = "salkim-domates")
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlan adı", example = "Salkım Domates")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlanın durumu", example = "PUBLISHED")
        ProductStatus status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Kaldırma gerekçesi",
                example = "Fotoğraflar ürünü göstermiyor.")
        String statusReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretici")
        SellerSummary seller,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Kapak fotoğrafı")
        ProductPhotoView coverPhoto,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Son değişiklik anı (UTC, ISO-8601)",
                example = "2026-10-08T09:30:00Z")
        Instant updatedAt) {
}
