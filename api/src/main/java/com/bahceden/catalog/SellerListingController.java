package com.bahceden.catalog;

import com.bahceden.catalog.domain.ProductTransition;
import com.bahceden.catalog.dto.ListingRequest;
import com.bahceden.catalog.dto.PhotoOrderRequest;
import com.bahceden.catalog.dto.PhotoUploadRequest;
import com.bahceden.catalog.dto.SellerListingView;
import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
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
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Satıcının kendi ilanları (product-listings spec'i, add-product-listings D2).
 *
 * <p>{@code /sellers/me} deseni: satıcı token'daki hesaptan bulunur, istemci
 * satıcı kimliği göndermez. Uçlar {@code ROLE_SELLER} ister
 * ({@code SecurityConfig}); otorite yine serviste denetlenen satıcı durumudur
 * (ADR-044).
 */
@RestController
@Validated
@RequestMapping("/api/v1/sellers/me/products")
@Tag(name = "Satıcı ilanları", description = "Onaylı satıcının kendi ürün ilanları")
public class SellerListingController {

    private static final String SELF_PATH = "/api/v1/sellers/me/products/";

    private final ListingService service;
    private final RateLimiter rateLimiter;

    SellerListingController(ListingService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "listOwnListings", summary = "Satıcının ilanlarını listeler",
            description = "En son güncellenen üstte. Sayfa boyutunun üst sınırı sunucuda 50'dir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<SellerListingView> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listOwn(accountId(token), page, size);
    }

    @Operation(operationId = "createListing", summary = "Taslak ilan oluşturur",
            description = "İlan DRAFT açılır ve vitrinde görünmez; yayına almak için en az bir fotoğraf gerekir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @ApiResponse(responseCode = "201", description = "İlan oluşturuldu; Location başlığı ilanın adresi")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SellerListingView> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                                    @Valid @RequestBody ListingRequest request) {
        var created = service.create(accountId(token), request);
        return ResponseEntity.created(URI.create(SELF_PATH + created.id())).body(created);
    }

    @Operation(operationId = "getOwnListing", summary = "Satıcının tek ilanını döner",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                 @PathVariable long id) {
        return service.getOwn(accountId(token), id);
    }

    @Operation(operationId = "updateListing", summary = "İlanın bilgilerini günceller",
            description = "Durum ve kalıcı ad değişmez. Yayındaki ilanın değişikliği vitrinde hemen görünür.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                    @PathVariable long id, @Valid @RequestBody ListingRequest request) {
        return service.update(accountId(token), id, request);
    }

    @Operation(operationId = "addListingPhoto", summary = "İlana fotoğraf ekler",
            description = """
                    Tek dosya, multipart. Fotoğraf sona eklenir; ilan başına en çok 5. Sunucuda EXIF yönü
                    uygulanır, iki boyutta JPEG olarak yeniden kodlanır ve meta veri atılır.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @ApiResponse(responseCode = "409", description = "Fotoğraf sınırı doldu (PRODUCT_PHOTO_LIMIT_REACHED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView addPhoto(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                      @PathVariable long id, @Valid @ModelAttribute PhotoUploadRequest request) {
        var accountId = accountId(token);
        rateLimiter.check(RateLimitPolicy.PRODUCT_PHOTO_UPLOAD, Long.toString(accountId));
        return service.addPhoto(accountId, id, request.photo());
    }

    @Operation(operationId = "deleteListingPhoto", summary = "İlanın fotoğrafını siler",
            description = "Kalan fotoğraflar sıkıştırılır. Yayındaki veya kaldırılmış ilanın son fotoğrafı silinemez.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @ApiResponse(responseCode = "409", description = "Son fotoğraf silinemez (PRODUCT_PHOTO_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping(path = "/{id}/photos/{photoId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView deletePhoto(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                         @PathVariable long id, @PathVariable long photoId) {
        return service.deletePhoto(accountId(token), id, photoId);
    }

    @Operation(operationId = "reorderListingPhotos", summary = "Fotoğrafların sırasını değiştirir",
            description = "Liste ilanın bütün fotoğraflarını eksiksiz ve tekrarsız içermelidir; ilk eleman kapak olur.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ListingResponses
    @PutMapping(path = "/{id}/photos/order", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView reorderPhotos(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                           @PathVariable long id, @Valid @RequestBody PhotoOrderRequest request) {
        return service.reorderPhotos(accountId(token), id, request.photoIds());
    }

    @Operation(operationId = "publishListing", summary = "Taslağı yayına alır",
            description = "DRAFT → PUBLISHED; en az bir fotoğraf gerekir. İlk yayın tarihi sonra değişmez.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @TransitionResponses
    @PostMapping(path = "/{id}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView publish(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                     @PathVariable long id) {
        return service.transition(accountId(token), id, ProductTransition.PUBLISH);
    }

    @Operation(operationId = "unpublishListing", summary = "İlanı yayından çeker", description = "PUBLISHED → DRAFT.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @TransitionResponses
    @PostMapping(path = "/{id}/unpublish", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView unpublish(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                       @PathVariable long id) {
        return service.transition(accountId(token), id, ProductTransition.UNPUBLISH);
    }

    @Operation(operationId = "archiveListing", summary = "İlanı arşivler", description = "DRAFT veya PUBLISHED → ARCHIVED.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @TransitionResponses
    @PostMapping(path = "/{id}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView archive(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                     @PathVariable long id) {
        return service.transition(accountId(token), id, ProductTransition.ARCHIVE);
    }

    @Operation(operationId = "restoreListing", summary = "İlanı arşivden geri alır", description = "ARCHIVED → DRAFT.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @TransitionResponses
    @PostMapping(path = "/{id}/restore", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerListingView restore(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                     @PathVariable long id) {
        return service.transition(accountId(token), id, ProductTransition.RESTORE);
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    /** İlan uçlarının ortak yanıtları: aynı liste her uçta tekrar yazılmaz. */
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan veya ilanlar"),
            @ApiResponse(responseCode = "400", description = "Alan geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403",
                    description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "İlan yok veya başkasına ait (PRODUCT_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface ListingResponses {
    }

    /** Yayın geçişlerinin yanıtları: ilan yanıtlarına ek olarak 409. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ListingResponses
    @ApiResponse(responseCode = "409",
            description = "Bu durumdan bu geçiş yapılamaz (PRODUCT_INVALID_TRANSITION) veya fotoğraf gerekiyor (PRODUCT_PHOTO_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @interface TransitionResponses {
    }
}
