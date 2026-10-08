package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * İlan kaldırma gerekçesi (ADR-038 §3). Gerekçe satıcıya gösterilir ve
 * e-postada geçer; admin bunu bilerek yazar.
 *
 * @param reason satıcıya gösterilecek gerekçe
 */
@Schema(name = "ListingRemovalRequest", description = "İlan kaldırma gerekçesi; satıcıya gösterilir")
public record ListingRemovalRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcıya gösterilecek gerekçe",
                example = "Fotoğraflar ilandaki ürünü göstermiyor.")
        @NotBlank(message = "{validation.decisionReason.required}")
        @Size(min = 10, max = 500, message = "{validation.decisionReason.length}")
        String reason) {
}
