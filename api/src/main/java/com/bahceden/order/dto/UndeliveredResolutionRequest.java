package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Teslim edilemeyen satıcı siparişi için admin kararı (add-shipments D7).
 * {@code CLOSE} için gerekçe zorunludur ve en az 10 karakterdir (serviste
 * denetlenir, {@code reason} alanına bağlı hata döner); {@code REFUND} için
 * isteğe bağlıdır.
 *
 * @param decision karar
 * @param reason   gerekçe; alıcıya ve satıcıya e-postayla iletilir
 */
@Schema(name = "UndeliveredResolutionRequest", description = "Teslim edilemeyen sipariş için admin kararı")
public record UndeliveredResolutionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "REFUND: ürünler ve kargo ücreti iade edilir; CLOSE: iadesiz kapatılır", example = "REFUND")
        @NotNull(message = "{validation.resolutionDecision.required}")
        Decision decision,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Gerekçe; CLOSE için zorunlu (10–500 karakter)",
                example = "Alıcı adreste bulunamadı ve paketi şubeden almadı.")
        @Size(max = 500, message = "{validation.decisionReason.length}")
        String reason) {

    /** Admin kararı. */
    @Schema(name = "UndeliveredDecision", description = "Teslim edilemeyen sipariş kararı")
    public enum Decision {
        REFUND,
        CLOSE
    }
}
