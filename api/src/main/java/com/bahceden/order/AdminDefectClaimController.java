package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.DefectClaimService.AdminDecision;
import com.bahceden.order.domain.DefectClaimStatus;
import com.bahceden.order.dto.AdminClaimDecisionRequest;
import com.bahceden.order.dto.AdminClaimDetail;
import com.bahceden.order.dto.AdminClaimSummary;
import com.bahceden.order.dto.ClaimPreviewRequest;
import com.bahceden.order.dto.ClaimSettlementView;
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
 * Admin'in ayıplı ürün talepleri (defect-claims spec'i "Admin yönetime taşınan
 * talebi karara bağlar"; ADR-080, ADR-086). Bütün uçlar {@code ROLE_ADMIN}
 * ister; kural {@code SecurityConfig}'te. Liste ve detay alıcının kişisel
 * verisini taşımaz.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/claims")
@Tag(name = "Admin: ayıplı ürün talepleri", description = "Yönetime taşınan taleplerin kararı")
public class AdminDefectClaimController {

    private final DefectClaimService service;

    AdminDefectClaimController(DefectClaimService service) {
        this.service = service;
    }

    @Operation(operationId = "listDefectClaimsForAdmin", summary = "Ayıplı ürün taleplerini listeler",
            description = """
                    Durum süzgeci varsayılan olarak ESCALATED'dır (karar bekleyen); diğer durumlar da süzülebilir.
                    En yeni üstte; sayfa boyutunun üst sınırı sunucuda 50'dir.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminClaimResponses
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<AdminClaimSummary> list(
            @Parameter(description = "Durum süzgeci", example = "ESCALATED")
            @RequestParam(defaultValue = "ESCALATED") DefectClaimStatus status,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listForAdmin(status, page, size);
    }

    @Operation(operationId = "getDefectClaimForAdmin", summary = "Ayıplı ürün talebinin detayı",
            description = "Talep, fotoğraflar, iki tarafın beyanı ve satın alınan adetler; alıcının kişisel verisi yok.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminClaimResponses
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminClaimDetail get(@PathVariable long id) {
        return service.detailForAdmin(id);
    }

    @Operation(operationId = "previewDefectClaimDecision", summary = "Admin kararının para etkisini önizler",
            description = "Yan etkisizdir. Adet her satırda satın alınana kadar olabilir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminClaimResponses
    @PostMapping(path = "/{id}/preview", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ClaimSettlementView preview(@PathVariable long id, @Valid @RequestBody ClaimPreviewRequest request) {
        return service.previewForAdmin(id, request.lines());
    }

    @Operation(operationId = "decideDefectClaim", summary = "Admin yönetime taşınan talebe karar verir",
            description = """
                    REFUND: satır başına satın alınana kadar adet iade edilir (toplam en az bir); tam iadede kargo da
                    döner, stok geri eklenmez. REJECT: gerekçe zorunlu (10–500 karakter), iade yapılmaz. Karar ve
                    gerekçe geçmişe yazılır; alıcıya ve satıcıya e-posta gider.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminClaimResponses
    @ApiResponse(responseCode = "409", description = "Talep karar bekliyor değil (CLAIM_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "İade yapılamadı; hiçbir şey değişmedi (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/decision", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminClaimDetail decide(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id,
                                   @Valid @RequestBody AdminClaimDecisionRequest request) {
        return service.decideAsAdmin(Long.parseLong(token.getSubject()), id, AdminDecision.valueOf(request.decision()),
                request.lines(), request.reason());
    }

    /** Admin talep uçlarının ortak yanıtları. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Talep, talepler veya para etkisi"),
            @ApiResponse(responseCode = "400", description = "Karar, satırlar, gerekçe veya süzgeç geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Talep yok (CLAIM_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface AdminClaimResponses {
    }
}
