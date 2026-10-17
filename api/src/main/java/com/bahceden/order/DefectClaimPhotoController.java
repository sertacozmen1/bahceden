package com.bahceden.order;

import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.storage.FileType;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.ProtectedFileResponses;
import com.bahceden.order.domain.DefectClaimPhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Talep fotoğrafı (ADR-084): yalnızca talebin alıcısına, satıcısına ve admin'e.
 * Başkasına 404 döner; varlık sızmaz. Önbelleğe alınmaz, içerik türü korunur
 * ({@link ProtectedFileResponses}).
 */
@RestController
@Tag(name = "Ayıplı ürün talepleri", description = "Teslim edilen siparişte sorun bildirme ve çözüm")
public class DefectClaimPhotoController {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String SERVED_NAME = "talep-fotografi";

    private final DefectClaimPhotoRepository photos;
    private final FileStorage storage;

    DefectClaimPhotoController(DefectClaimPhotoRepository photos, FileStorage storage) {
        this.photos = photos;
        this.storage = storage;
    }

    /** Fotoğraf boyutu: küçük (kart) veya büyük. */
    public enum PhotoSize {
        CARD,
        LARGE
    }

    @Operation(operationId = "getDefectClaimPhoto", summary = "Talep fotoğrafını döner",
            description = "Yalnızca talebin alıcısı, satıcısı ve admin görür. JPEG; önbelleğe alınmaz.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Fotoğraf (JPEG)",
            content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Fotoğraf yok veya hesap talebin tarafı değil (CLAIM_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/api/v1/claims/{claimId}/photos/{position}")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> photo(@Parameter(hidden = true) Authentication authentication,
                                                     @PathVariable long claimId, @PathVariable int position,
                                                     @Parameter(description = "Boyut: CARD veya LARGE", example = "LARGE")
                                                     @RequestParam(defaultValue = "LARGE") PhotoSize size) {
        var admin = authentication.getAuthorities().stream().anyMatch(role -> ADMIN_ROLE.equals(role.getAuthority()));
        var photo = (admin
                ? photos.findForAdmin(claimId, position)
                : photos.findVisibleTo(claimId, position, Long.parseLong(authentication.getName())))
                .orElseThrow(DefectClaimPhotoController::notFound);
        var bytes = read(size == PhotoSize.CARD ? photo.getCardKey() : photo.getLargeKey(), photo);
        return ProtectedFileResponses.inline(FileType.JPEG, bytes.length, new ByteArrayInputStream(bytes), SERVED_NAME);
    }

    /** Fotoğraflar küçüktür (en çok 1600 px JPEG); uzunluk başlığı için belleğe okunur. */
    private byte[] read(String key, DefectClaimPhoto photo) {
        try (var content = storage.openIfExists(key).orElseThrow(DefectClaimPhotoController::notFound)) {
            return content.readAllBytes();
        } catch (IOException cause) {
            throw new UncheckedIOException("Talep fotoğrafı okunamadı: konum " + photo.getPosition(), cause);
        }
    }

    private static ApiException notFound() {
        return new ApiException(ApiErrorCode.CLAIM_NOT_FOUND, "error.claimNotFound");
    }
}
