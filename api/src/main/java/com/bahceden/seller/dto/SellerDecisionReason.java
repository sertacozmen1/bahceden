package com.bahceden.seller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ret ve askı eyleminin gerekçesi (ADR-037 §5). Gerekçe satıcıya gösterilir ve
 * karar e-postasında yer alır; admin bunu bilerek yazar.
 *
 * @param reason satıcıya gösterilecek gerekçe
 */
@Schema(name = "SellerDecisionReason", description = "Ret veya askı gerekçesi; satıcıya gösterilir")
public record SellerDecisionReason(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Satıcıya gösterilecek gerekçe", example = "Belgedeki ad ile beyan edilen ad eşleşmiyor.")
        @NotBlank(message = "{validation.decisionReason.required}")
        @Size(min = 10, max = 500, message = "{validation.decisionReason.length}")
        String reason) {
}
