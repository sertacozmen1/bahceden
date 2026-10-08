package com.bahceden.seller;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.seller.domain.SellerStatus;
import com.bahceden.seller.domain.SellerTransition;
import com.bahceden.seller.dto.AdminSellerDetail;
import com.bahceden.seller.dto.AdminSellerSummary;
import com.bahceden.seller.dto.SellerDecisionReason;
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
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Admin: satıcı başvurularını karara bağlama (seller-onboarding spec'i "Admin
 * başvuruları karara bağlar", ADR-037, add-seller-onboarding D7).
 *
 * <p>Bütün uçlar {@code ROLE_ADMIN} ister; kural {@code SecurityConfig}'te
 * açıkça yazılıdır, rolsüz istek 403 alır. Durum geçişleri komut uçlarıdır
 * (AGENTS.md §11.5): her eylem kendi adresinde, izinli geçişler
 * {@link SellerTransition}'da tek yerde.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/sellers")
@Tag(name = "Admin: satıcılar", description = "Satıcı başvurularının listelenmesi ve karara bağlanması")
public class AdminSellerController {

    private final SellerService service;

    AdminSellerController(SellerService service) {
        this.service = service;
    }

    @Operation(
            operationId = "listSellersForAdmin",
            summary = "Satıcı kayıtlarını listeler",
            description = "Durumla süzülebilir; yeniden eskiye sıralı. Sayfa boyutunun üst sınırı sunucuda 50'dir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Satıcı kayıtları"),
            @ApiResponse(responseCode = "400", description = "Geçersiz süzgeç veya sayfa (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<AdminSellerSummary> list(
            @Parameter(description = "Durum süzgeci; verilmezse tüm kayıtlar", example = "PENDING")
            @RequestParam(required = false) SellerStatus status,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listForAdmin(Optional.ofNullable(status), page, size);
    }

    @Operation(
            operationId = "getSellerForAdmin",
            summary = "Satıcı kaydının ayrıntısını ve geçmişini döner",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Satıcı kaydı"),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Kayıt yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerDetail detail(@PathVariable long id) {
        return service.detailForAdmin(id);
    }

    @Operation(
            operationId = "getSellerDocumentForAdmin",
            summary = "Satıcının güncel başvuru belgesini döner",
            description = "Sahibin belge ucuyla aynı koruma başlıklarıyla döner.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Belge dosyası",
                    content = {
                            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Kayıt veya belge yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/document")
    public ResponseEntity<InputStreamResource> document(@PathVariable long id) {
        return SellerDocumentResponses.inline(service.documentForAdmin(id));
    }

    @Operation(
            operationId = "approveSeller",
            summary = "Bekleyen başvuruyu onaylar",
            description = "PENDING → APPROVED. Hesaba ROLE_SELLER eklenir ve karar e-postayla bildirilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @DecisionResponses
    @PostMapping(path = "/{id}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerDetail approve(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                     @PathVariable long id) {
        return service.decide(accountId(token), id, SellerTransition.APPROVE, null);
    }

    @Operation(
            operationId = "rejectSeller",
            summary = "Bekleyen başvuruyu gerekçeyle reddeder",
            description = "PENDING → REJECTED. Gerekçe satıcıya gösterilir ve e-postayla bildirilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @DecisionResponses
    @PostMapping(path = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerDetail reject(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                    @PathVariable long id, @Valid @RequestBody SellerDecisionReason request) {
        return service.decide(accountId(token), id, SellerTransition.REJECT, request.reason());
    }

    @Operation(
            operationId = "suspendSeller",
            summary = "Onaylı satıcıyı gerekçeyle askıya alır",
            description = """
                    APPROVED → SUSPENDED. ROLE_SELLER kaldırılır, ürünleri vitrinden düşer; gerekçe
                    satıcıya gösterilir ve e-postayla bildirilir.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @DecisionResponses
    @PostMapping(path = "/{id}/suspend", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerDetail suspend(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                     @PathVariable long id, @Valid @RequestBody SellerDecisionReason request) {
        return service.decide(accountId(token), id, SellerTransition.SUSPEND, request.reason());
    }

    @Operation(
            operationId = "reinstateSeller",
            summary = "Askıdaki satıcıyı askıdan çıkarır",
            description = "SUSPENDED → APPROVED. ROLE_SELLER yeniden eklenir ve karar e-postayla bildirilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @DecisionResponses
    @PostMapping(path = "/{id}/reinstate", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerDetail reinstate(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                       @PathVariable long id) {
        return service.decide(accountId(token), id, SellerTransition.REINSTATE, null);
    }

    /** Token'ın {@code sub} iddiası hesap kimliğidir; geçmişe "kim" olarak yazılır. */
    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    /** Dört karar ucunun ortak yanıtları: aynı liste dört kez yazılmaz. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Karar kaydedildi; güncel kayıt döner"),
            @ApiResponse(responseCode = "400", description = "Gerekçe eksik veya kurala uymuyor (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Kayıt yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Bu durumdan bu geçiş yapılamaz (SELLER_INVALID_TRANSITION)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface DecisionResponses {
    }
}
