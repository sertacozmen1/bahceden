package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sepetteki bir ürünün adedi (cart-and-checkout spec'i, D4). Ürün adresten
 * gelir; satır yoksa eklenir, varsa adedi değişir.
 *
 * @param quantity adet: 1–99 ve ürünün stoğunu aşmaz (stok serviste denetlenir)
 */
@Schema(name = "CartItemRequest", description = "Sepetteki ürünün adedi")
public record CartItemRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adet: 1–99, stoğu aşamaz", example = "2")
        @NotNull(message = "{validation.cartQuantity.required}")
        @Min(value = 1, message = "{validation.cartQuantity.range}")
        @Max(value = 99, message = "{validation.cartQuantity.range}")
        Integer quantity) {
}
