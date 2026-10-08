package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Oluşan sipariş ve ödeme sayfası (D6, D8). Aynı {@code Idempotency-Key} ile
 * tekrarlanan istek aynı değerleri döner.
 *
 * @param orderNumber        siparişin iş anahtarı
 * @param paymentRedirectUrl alıcının gideceği ödeme sayfası
 */
@Schema(name = "OrderPlaced", description = "Oluşan sipariş ve ödeme sayfası")
public record OrderPlaced(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcının yönlendirileceği ödeme sayfası",
                example = "https://bahceden.example/checkout/return?order=BHC-7K2M9QXA4T")
        String paymentRedirectUrl) {
}
