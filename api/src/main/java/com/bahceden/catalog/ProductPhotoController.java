package com.bahceden.catalog;

import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * İlan fotoğraflarının herkese açık sunumu (add-product-listings D5).
 *
 * <p>Anahtar sunucunun ürettiği 128 bitlik rastgele değerdir ve değişmez; bu
 * yüzden yanıt bir yıl önbelleklenir. Anahtar {@code product_photos}
 * tablosunda kayıtlı olmalıdır: satıcı belgeleri aynı dizinde, aynı anahtar
 * biçimiyle durur ve bu uçtan <strong>asla</strong> sunulmaz.
 */
@RestController
@RequestMapping("/api/v1/product-photos")
@Tag(name = "Ürünler", description = "Vitrindeki ürünlerin listelenmesi")
public class ProductPhotoController {

    private static final Duration CACHE_FOR = Duration.ofDays(365);

    private final ProductPhotoRepository photos;
    private final FileStorage storage;

    ProductPhotoController(ProductPhotoRepository photos, FileStorage storage) {
        this.photos = photos;
        this.storage = storage;
    }

    @Operation(operationId = "getProductPhoto", summary = "İlan fotoğrafını döner",
            description = "JPEG; değişmez anahtar, bir yıllık önbellek. Anahtar yalnızca ilan fotoğrafına aitse sunulur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fotoğraf",
                    content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Fotoğraf yok (PRODUCT_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{key}")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> photo(@PathVariable String key) {
        if (!photos.isPhotoKey(key)) {
            throw notFound();
        }
        var content = storage.openIfExists(key).orElseThrow(ProductPhotoController::notFound);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content));
    }

    private static ApiException notFound() {
        return new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "error.productNotFound");
    }
}
