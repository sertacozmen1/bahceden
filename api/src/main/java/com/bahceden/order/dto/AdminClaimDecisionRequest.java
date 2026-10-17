package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Admin'in yönetime taşınan talebe kararı (ADR-080). Karar metin olarak alınıp
 * kalıpla doğrulanır; karara bağlı kurallar serviste denetlenir.
 *
 * @param decision {@code REFUND}: satır başına satın alınana kadar adet; {@code REJECT}: gerekçeli ret
 * @param lines    iadede satır başına iade adedi; yazılmayan satır 0 sayılır
 * @param reason   retde zorunlu gerekçe (10–500 karakter); iadede isteğe bağlı açıklama
 */
@Schema(name = "AdminClaimDecisionRequest", description = "Admin'in ayıplı ürün talebine kararı")
public record AdminClaimDecisionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Karar",
                allowableValues = {"REFUND", "REJECT"}, example = "REFUND")
        @NotBlank(message = "{validation.adminClaimDecision.allowed}")
        @Pattern(regexp = "REFUND|REJECT", message = "{validation.adminClaimDecision.allowed}")
        String decision,

        @ArraySchema(schema = @Schema(implementation = ClaimResponseRequest.Line.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "İadede satır başına iade adedi"))
        @Size(max = 50, message = "{validation.claimLines.required}")
        @Valid
        List<ClaimResponseRequest.Line> lines,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                description = "Retde zorunlu gerekçe (10–500 karakter); iadede isteğe bağlı açıklama",
                example = "Fotoğraflar kasanın ezik geldiğini gösteriyor.")
        String reason) {
}
