package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vitrinde ürünle birlikte gösterilen üretici bilgisi.
 *
 * <p>Ürünün kimden geldiğini göstermek ürünün ayırt edici değeridir
 * (docs/product/index.md): zincirden hal ve komisyoncu çıkınca alıcı doğrudan
 * üreticiyi görür.
 *
 * @param slug        üreticinin kalıcı tanımlayıcısı
 * @param displayName vitrinde gösterilen ad
 * @param city        üretim yapılan il
 */
@Schema(name = "SellerSummary", description = "Ürünün üreticisi")
public record SellerSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üreticinin kalıcı tanımlayıcısı", example = "ornek-uretici-manisa") String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Vitrinde gösterilen ad", example = "Örnek Üretici (Manisa)") String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretim yapılan il", example = "Manisa") String city) {
}
