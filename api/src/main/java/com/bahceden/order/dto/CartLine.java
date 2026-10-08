package com.bahceden.order.dto;

import com.bahceden.catalog.domain.ProductUnit;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.ProductPhotoView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sepetin bir satırı; fiyat ve stok ürünün güncel değeridir (D4).
 *
 * @param productId     ürünün kimliği (adet değiştirme ve silme bu kimlikle)
 * @param slug          ürün adresi
 * @param name          ürün adı
 * @param unit          satış birimi
 * @param unitPrice     güncel birim fiyat
 * @param quantity      adet
 * @param lineTotal     satır tutarı: birim fiyat × adet
 * @param stockQuantity güncel stok
 * @param available     satın alınabilir mi; değilse toplamlara katılmaz
 * @param coverPhoto    kapak fotoğrafı; yoksa {@code null}
 */
@Schema(name = "CartLine", description = "Sepet satırı")
public record CartLine(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürünün kimliği", example = "42")
        long productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adresi", example = "salkim-domates")
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adı", example = "Salkım Domates")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM")
        ProductUnit unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Güncel birim fiyat")
        MoneyView unitPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adet", example = "2")
        int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satır tutarı: birim fiyat × adet")
        MoneyView lineTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Güncel stok", example = "40")
        int stockQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Satın alınabilir mi; değilse toplamlara katılmaz ve sipariş verilemez", example = "true")
        boolean available,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Kapak fotoğrafı; yoksa boş")
        ProductPhotoView coverPhoto) {
}
