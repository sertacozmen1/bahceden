package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Kargodan önce iptal (orders spec'i, ADR-056 §1, add-cart-and-checkout D9).
 * Alıcı için gerekçe isteğe bağlıdır; satıcı için zorunludur ve en az 10
 * karakterdir (serviste denetlenir, {@code reason} alanına bağlı hata döner).
 *
 * @param reason iptal gerekçesi; karşı tarafa e-postayla iletilir
 */
@Schema(name = "CancellationRequest", description = "Kargodan önce iptal")
public record CancellationRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "İptal gerekçesi; satıcı için zorunlu (10–500 karakter), alıcı için isteğe bağlı",
                example = "Hasat beklenenden az oldu, ürün kalmadı.")
        @Size(max = 500, message = "{validation.cancelReason.length}")
        String reason) {
}
