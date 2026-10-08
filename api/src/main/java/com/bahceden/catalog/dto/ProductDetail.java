package com.bahceden.catalog.dto;

import com.bahceden.catalog.domain.ProductUnit;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Vitrindeki ürünün detayı (storefront-catalog spec'i "Ürün detayı",
 * add-product-listings D6). Yalnızca vitrinde görünen ürün için döner.
 *
 * @param slug          ürünün kalıcı tanımlayıcısı
 * @param name          ürün adı
 * @param description   açıklama; yoksa boş
 * @param unit          satış birimi
 * @param unitPrice     birim fiyat
 * @param stockQuantity eldeki miktar; sıfırsa stokta değil
 * @param seller        üretici
 * @param photos        fotoğraflar, sırayla; ilki kapak
 */
@Schema(name = "ProductDetail", description = "Vitrindeki ürünün detayı")
public record ProductDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürünün kalıcı tanımlayıcısı", example = "salkim-domates")
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adı", example = "Salkım Domates")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Açıklama; düz metin",
                example = "Tarla domatesi, sabah toplandı.")
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM")
        ProductUnit unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Birim fiyat")
        MoneyView unitPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Eldeki miktar; sıfırsa stokta değil", example = "80")
        int stockQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürünü üreten ve gönderen üretici")
        SellerSummary seller,
        @ArraySchema(schema = @Schema(implementation = ProductPhotoView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fotoğraflar, sırayla; ilki kapak"))
        List<ProductPhotoView> photos) {
}
