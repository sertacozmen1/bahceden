package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ayıplı ürün talebi açma (ADR-081). {@code multipart/form-data}: satırlar
 * {@code lines[0].orderItemId}, {@code lines[0].quantity} biçiminde, fotoğraflar
 * tekrarlanan {@code photos} parçalarıdır. Satırın siparişe aitliği, adetin
 * satın alınanı aşmaması ve fotoğraf sayısı serviste denetlenir.
 *
 * @param description alıcının açıklaması
 * @param lines       sorunlu satırlar ve adetleri
 * @param photos      1–3 fotoğraf: JPEG veya PNG
 */
@Schema(name = "DefectClaimForm", description = "Ayıplı ürün talebi açma")
public record DefectClaimForm(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sorunun açıklaması, 10–1000 karakter",
                example = "Domates kasasının üst sırası ezik geldi.")
        @NotBlank(message = "{validation.claimDescription.required}")
        @Size(min = 10, max = 1000, message = "{validation.claimDescription.length}")
        String description,

        @ArraySchema(schema = @Schema(implementation = Line.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Sorunlu satırlar; aynı satır bir kez"))
        @NotEmpty(message = "{validation.claimLines.required}")
        @Size(max = 50, message = "{validation.claimLines.required}")
        @Valid
        List<Line> lines,

        @ArraySchema(schema = @Schema(type = "string", format = "binary"),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "1–3 fotoğraf: JPEG veya PNG; her biri en çok 10 MB ve 40 megapiksel"))
        List<MultipartFile> photos) {

    /**
     * @param orderItemId sipariş satırının kimliği
     * @param quantity    sorunlu adet; satın alınan adeti aşamaz
     */
    @Schema(name = "DefectClaimFormLine", description = "Talepteki bir satır")
    public record Line(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş satırının kimliği", example = "42")
            @NotNull(message = "{validation.claimLine.required}")
            Long orderItemId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sorunlu adet", example = "2")
            @NotNull(message = "{validation.claimQuantity.range}")
            @Min(value = 1, message = "{validation.claimQuantity.range}")
            @Max(value = 99, message = "{validation.claimQuantity.range}")
            Integer quantity) {
    }
}
