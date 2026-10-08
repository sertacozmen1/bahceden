package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Sepetten sipariş oluşturma (cart-and-checkout spec'i, D6). Satırlar,
 * fiyatlar ve tutarlar sunucudaki sepetten ve üründen gelir; istemci tutar
 * göndermez (AGENTS.md §11.1).
 *
 * @param addressId teslimat adresinin kimliği; hesabın kendi adresi olmalı
 */
@Schema(name = "PlaceOrderRequest", description = "Sepetten sipariş oluşturma")
public record PlaceOrderRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslimat adresinin kimliği", example = "5")
        @NotNull(message = "{validation.orderAddress.required}")
        Long addressId) {
}
