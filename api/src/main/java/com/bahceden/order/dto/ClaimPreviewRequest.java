package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yanıtın para etkisini önizleme (ADR-086): yan etkisizdir.
 *
 * @param lines satır başına iade edilecek adet; boşsa alıcının istediği adetler (tamamını kabul)
 */
@Schema(name = "ClaimPreviewRequest", description = "Ayıplı ürün yanıtının para etkisini önizleme")
public record ClaimPreviewRequest(
        @ArraySchema(schema = @Schema(implementation = ClaimResponseRequest.Line.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Satır başına adet; boşsa alıcının istediği adetler"))
        @Size(max = 50, message = "{validation.claimLines.required}")
        @Valid
        List<ClaimResponseRequest.Line> lines) {
}
