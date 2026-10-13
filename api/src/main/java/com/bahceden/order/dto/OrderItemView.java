package com.bahceden.order.dto;

import com.bahceden.catalog.domain.ProductUnit;
import com.bahceden.catalog.dto.MoneyView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sipariş satırı; değerler sipariş anındaki kopyadır (D6).
 *
 * @param id          sipariş satırının kimliği (ayıplı ürün talebi satırı bununla seçer, ADR-081)
 * @param productSlug ürün adresi (ürün sonradan kaldırılmış olabilir)
 * @param productName sipariş anındaki ürün adı
 * @param unit        satış birimi
 * @param unitPrice   sipariş anındaki birim fiyat
 * @param quantity    adet
 * @param lineTotal   satır tutarı
 */
@Schema(name = "OrderItemView", description = "Sipariş satırı; değerler sipariş anındaki kopyadır")
public record OrderItemView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş satırının kimliği", example = "42")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adresi", example = "salkim-domates")
        String productSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adı", example = "Salkım Domates")
        String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM")
        ProductUnit unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Birim fiyat")
        MoneyView unitPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adet", example = "2")
        int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satır tutarı")
        MoneyView lineTotal) {
}
