package com.bahceden.catalog.dto;

import com.bahceden.catalog.domain.ProductStatus;
import com.bahceden.catalog.domain.ProductUnit;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Satıcının kendi ilanı (product-listings spec'i). Vitrin görünümünden farklı
 * olarak durumu, kaldırma gerekçesini ve kimliği taşır; kimlik yalnızca
 * sahibine verilir ve düzenleme adreslerinde kullanılır.
 *
 * @param id            ilanın kimliği
 * @param slug          vitrin adresindeki kalıcı ad
 * @param name          ilan adı
 * @param description   açıklama
 * @param unit          satış birimi
 * @param unitPrice     birim fiyat
 * @param stockQuantity eldeki miktar
 * @param status        ilanın durumu
 * @param statusReason  admin kaldırma gerekçesi
 * @param publishedAt   ilk yayın anı; hiç yayınlanmadıysa boş
 * @param updatedAt     son değişiklik anı
 * @param photos        fotoğraflar, sırayla; ilki kapak
 */
@Schema(name = "SellerListingView", description = "Satıcının kendi ilanı")
public record SellerListingView(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlanın kimliği", example = "42")
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Vitrin adresindeki kalıcı ad",
                example = "salkim-domates")
        String slug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlan adı", example = "Salkım Domates")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Açıklama",
                example = "Tarla domatesi, sabah toplandı.")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM")
        ProductUnit unit,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Birim fiyat")
        MoneyView unitPrice,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Eldeki miktar", example = "80")
        int stockQuantity,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlanın durumu", example = "DRAFT")
        ProductStatus status,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Admin kaldırma gerekçesi; yalnızca REMOVED durumunda",
                example = "Fotoğraflar ürünü göstermiyor.")
        String statusReason,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "İlk yayın anı (UTC, ISO-8601); hiç yayınlanmadıysa boş", example = "2026-10-08T09:30:00Z")
        Instant publishedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Son değişiklik anı (UTC, ISO-8601)",
                example = "2026-10-08T09:30:00Z")
        Instant updatedAt,

        @ArraySchema(schema = @Schema(implementation = ProductPhotoView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Fotoğraflar, sırayla; ilki kapak"))
        List<ProductPhotoView> photos) {
}
