package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Fotoğrafların yeni sırası (add-product-listings D3). Liste ilanın
 * fotoğraflarını eksiksiz ve tekrarsız içermelidir; ilk eleman kapak olur.
 *
 * @param photoIds fotoğraf kimlikleri, yeni sırayla
 */
@Schema(name = "PhotoOrderRequest", description = "Fotoğrafların yeni sırası; ilk eleman kapak")
public record PhotoOrderRequest(
        @ArraySchema(schema = @Schema(description = "Fotoğraf kimliği", example = "15"),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "İlanın bütün fotoğraf kimlikleri, yeni sırayla"))
        @NotNull(message = "{validation.photoOrder.invalid}")
        @Size(min = 1, max = 5, message = "{validation.photoOrder.invalid}")
        List<Long> photoIds) {
}
