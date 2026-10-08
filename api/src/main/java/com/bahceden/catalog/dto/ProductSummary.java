package com.bahceden.catalog.dto;

import com.bahceden.catalog.domain.ProductUnit;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vitrin listesindeki tek ürün.
 *
 * <p>Alanlar storefront-catalog spec'inin gerektirdiği kadardır: ad, birim,
 * birim fiyat, stok ve üretici. Spec'te olmayan alan sözleşmeye eklenmez
 * (AGENTS.md K2). Kapak fotoğrafı add-product-listings ile eklendi ve boş
 * olabilir: dev seed'inden gelen eski ürünlerin fotoğrafı yoktur (D6).
 *
 * @param slug          ürünün kalıcı tanımlayıcısı
 * @param name          ürün adı
 * @param unit          satış birimi
 * @param unitPrice     birim fiyat (kuruş cinsinden)
 * @param stockQuantity eldeki miktar; sıfırsa ürün stokta değildir
 * @param seller        ürünü üreten ve gönderen üretici
 * @param coverPhoto    kapak fotoğrafı; yoksa {@code null}
 * @param purchasable   satın alınabilir mi (add-cart-and-checkout D1)
 */
@Schema(name = "ProductSummary", description = "Vitrin listesindeki ürün")
public record ProductSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürünün kalıcı tanımlayıcısı", example = "sultani-uzum") String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adı", example = "Sultani Üzüm") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM") ProductUnit unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Birim fiyat") MoneyView unitPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Eldeki miktar; sıfırsa stokta değil", example = "120") int stockQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürünü üreten ve gönderen üretici") SellerSummary seller,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kapak fotoğrafı (sıradaki ilk fotoğraf); fotoğrafı olmayan üründe boş") ProductPhotoView coverPhoto,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Satın alınabilir mi: yayında, satıcısı onaylı, satıcının ödeme hesabı etkin ve stokta",
                example = "true") boolean purchasable) {
}
