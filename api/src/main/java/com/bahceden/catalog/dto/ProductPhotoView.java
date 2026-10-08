package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * İlan fotoğrafı (add-product-listings D5). Sözleşme dosya yolu taşımaz,
 * yalnızca anahtarları taşır; adresi istemci kurar (`api` istemci rotası
 * bilmez, AGENTS.md §3). Ölçüler büyük boyutundur; kart boyutu aynı orandadır.
 *
 * @param id       fotoğrafın kimliği; sıralama ve silmede kullanılır
 * @param cardKey  kart boyutunun (uzun kenar 400 px) anahtarı
 * @param largeKey büyük boyutun (uzun kenar 1600 px) anahtarı
 * @param width    büyük boyutun genişliği (px)
 * @param height   büyük boyutun yüksekliği (px)
 */
@Schema(name = "ProductPhotoView", description = "İlan fotoğrafı; anahtarlar GET /api/v1/product-photos/{key} ile sunulur")
public record ProductPhotoView(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fotoğrafın kimliği", example = "15")
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kart boyutunun anahtarı (uzun kenar 400 px)",
                example = "3f9a1c0b7d2e4f6a8b1c2d3e4f5a6b7c")
        String cardKey,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Büyük boyutun anahtarı (uzun kenar 1600 px)",
                example = "8b1c2d3e4f5a6b7c3f9a1c0b7d2e4f6a")
        String largeKey,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Büyük boyutun genişliği (px)", example = "1600")
        int width,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Büyük boyutun yüksekliği (px)", example = "1200")
        int height) {
}
