package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Satıcının talebe yanıtı (ADR-080). Karar metin olarak alınıp kalıpla
 * doğrulanır: tanınmayan değer okunamayan istek değil, alan hatası olur.
 * Karara bağlı kurallar (teklifte satırlar, retde gerekçe) serviste denetlenir.
 *
 * @param decision {@code ACCEPT}: istenenin tamamı; {@code OFFER}: daha az adet; {@code REJECT}: gerekçeli ret
 * @param lines    teklifte satır başına önerilen adet; yazılmayan satır 0 sayılır
 * @param reason   retde gerekçe, 10–500 karakter; alıcıya iletilir
 */
@Schema(name = "ClaimResponseRequest", description = "Satıcının ayıplı ürün talebine yanıtı")
public record ClaimResponseRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Karar",
                allowableValues = {"ACCEPT", "OFFER", "REJECT"}, example = "OFFER")
        @NotBlank(message = "{validation.claimDecision.allowed}")
        @Pattern(regexp = "ACCEPT|OFFER|REJECT", message = "{validation.claimDecision.allowed}")
        String decision,

        @ArraySchema(schema = @Schema(implementation = Line.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Teklifte satır başına önerilen adet"))
        @Size(max = 50, message = "{validation.claimLines.required}")
        @Valid
        List<Line> lines,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Retde gerekçe, 10–500 karakter",
                example = "Paket gönderilirken sağlamdı; fotoğraftaki kasa bize ait değil.")
        String reason) {

    /**
     * @param orderItemId talepteki sipariş satırının kimliği
     * @param quantity    önerilen adet; alıcının istediğini aşamaz
     */
    @Schema(name = "ClaimResponseLine", description = "Satır başına önerilen adet")
    public record Line(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş satırının kimliği", example = "42")
            @NotNull(message = "{validation.claimLine.required}")
            Long orderItemId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Önerilen adet", example = "1")
            @NotNull(message = "{validation.claimQuantity.range}")
            @Min(value = 0, message = "{validation.claimQuantity.range}")
            @Max(value = 99, message = "{validation.claimQuantity.range}")
            Integer quantity) {
    }
}
