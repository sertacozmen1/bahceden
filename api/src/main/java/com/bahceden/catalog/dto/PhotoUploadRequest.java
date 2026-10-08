package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tek fotoğraf yükleme (add-product-listings D3). {@code multipart/form-data};
 * tür ve boyut servis katmanında içerikten doğrulanır (D4).
 *
 * @param photo fotoğraf dosyası: JPEG veya PNG
 */
@Schema(name = "PhotoUploadRequest", description = "İlana tek fotoğraf yükleme")
public record PhotoUploadRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary",
                description = "Fotoğraf: JPEG veya PNG; en çok 10 MB ve 40 megapiksel")
        @NotNull(message = "{validation.photo.required}")
        MultipartFile photo) {
}
