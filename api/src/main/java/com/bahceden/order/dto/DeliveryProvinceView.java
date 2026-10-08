package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Teslimat yapılan il (add-cart-and-checkout D5). İl adı özel isimdir,
 * çevrilmez.
 *
 * @param code plaka kodu
 * @param name il adı
 */
@Schema(name = "DeliveryProvince", description = "Teslimat yapılan il")
public record DeliveryProvinceView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Plaka kodu", example = "35") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İl adı", example = "İzmir") String name) {
}
