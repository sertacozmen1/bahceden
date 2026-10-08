package com.bahceden.catalog;

import com.bahceden.catalog.domain.ProductStatus;
import com.bahceden.catalog.dto.AdminProductSummary;
import com.bahceden.catalog.dto.ListingRemovalRequest;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin: ilan denetimi (product-listings spec'i "Admin ilanı kaldırır ve
 * yeniden açar", ADR-038 §3, add-product-listings D7). Bütün uçlar
 * {@code ROLE_ADMIN} ister ({@code SecurityConfig}).
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin: ilanlar", description = "İlanların listelenmesi, kaldırılması ve yeniden açılması")
public class AdminProductController {

    private final ProductModerationService service;

    AdminProductController(ProductModerationService service) {
        this.service = service;
    }

    @Operation(operationId = "listProductsForAdmin", summary = "İlanları duruma göre listeler",
            description = "Varsayılan durum PUBLISHED; en son değişen üstte. Sayfa boyutunun üst sınırı sunucuda 50'dir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ModerationResponses
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<AdminProductSummary> list(
            @Parameter(description = "Durum süzgeci", example = "PUBLISHED")
            @RequestParam(defaultValue = "PUBLISHED") ProductStatus status,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.list(status, page, size);
    }

    @Operation(operationId = "removeProduct", summary = "İlanı gerekçeyle kaldırır",
            description = "DRAFT veya PUBLISHED → REMOVED. Gerekçe satıcıya gösterilir ve e-postayla bildirilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ModerationResponses
    @ApiResponse(responseCode = "409", description = "Bu durumdan kaldırılamaz (PRODUCT_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/remove", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminProductSummary remove(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                      @PathVariable long id, @Valid @RequestBody ListingRemovalRequest request) {
        return service.remove(Long.parseLong(token.getSubject()), id, request.reason());
    }

    @Operation(operationId = "reinstateProduct", summary = "Kaldırılmış ilanı yeniden açar",
            description = "REMOVED → PUBLISHED; en az bir fotoğraf gerekir. Gerekçe temizlenir, satıcıya bildirilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ModerationResponses
    @ApiResponse(responseCode = "409",
            description = "Bu durumdan açılamaz (PRODUCT_INVALID_TRANSITION) veya fotoğraf yok (PRODUCT_PHOTO_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/reinstate", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminProductSummary reinstate(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                         @PathVariable long id) {
        return service.reinstate(Long.parseLong(token.getSubject()), id);
    }

    /** Admin ilan uçlarının ortak yanıtları. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan veya ilanlar"),
            @ApiResponse(responseCode = "400", description = "Gerekçe veya süzgeç geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "İlan yok (PRODUCT_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface ModerationResponses {
    }
}
