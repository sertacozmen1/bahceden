package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.order.DefectClaimService.SellerDecision;
import com.bahceden.order.dto.ClaimPreviewRequest;
import com.bahceden.order.dto.ClaimResponseRequest;
import com.bahceden.order.dto.ClaimSettlementView;
import com.bahceden.order.dto.SellerOrderDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Satıcının ayıplı ürün talebine yanıtı (defect-claims spec'i; ADR-080, ADR-082,
 * ADR-086). {@code ROLE_SELLER} ister; otorite serviste denetlenen satıcı
 * durumudur (ADR-044) ve talep satıcı kimliğiyle sorgulanır (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/sellers/me/orders")
@Tag(name = "Ayıplı ürün talepleri", description = "Teslim edilen siparişte sorun bildirme ve çözüm")
public class DefectClaimSellerController {

    private final DefectClaimService service;

    DefectClaimSellerController(DefectClaimService service) {
        this.service = service;
    }

    @Operation(operationId = "respondToDefectClaim", summary = "Satıcı ayıplı ürün talebine yanıt verir",
            description = """
                    ACCEPT: istenen adetlerin tamamı iade edilir. OFFER: satır başına daha az adet önerilir; alıcının
                    48 saati olur, yanıt vermezse teklif uygulanır. REJECT: gerekçe (10–500 karakter) alıcıya
                    iletilir; alıcı 48 saat içinde yönetime taşıyabilir. Bütün satırlarda istenen adeti önermek
                    kabuldür. Stok geri eklenmez.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Satıcı siparişinin güncel hali")
    @ApiResponse(responseCode = "400", description = "Karar, satırlar veya gerekçe geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Talep yok veya başka satıcının (CLAIM_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Talep yanıt bekliyor değil (CLAIM_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "İade yapılamadı; hiçbir şey değişmedi (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/claim/response", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerOrderDetail respond(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id,
                                     @Valid @RequestBody ClaimResponseRequest request) {
        return service.respondAsSeller(Long.parseLong(token.getSubject()), id, SellerDecision.valueOf(request.decision()),
                request.lines(), request.reason());
    }

    @Operation(operationId = "previewDefectClaimSettlement", summary = "Satıcı yanıtının para etkisini önizler",
            description = """
                    Yan etkisizdir. Satır verilmezse alıcının istediği adetler (tamamını kabul) hesaplanır. Dönen
                    tutarlar sunucunun kuralıyladır: tam iadede kargo ücreti de iade edilir ve hakediş sıfırlanır;
                    kısmi iadede komisyon kalan tutar üzerinden yeniden hesaplanır.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "İadenin para etkisi")
    @ApiResponse(responseCode = "400", description = "Satırlar geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Talep yok veya başka satıcının (CLAIM_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/claim/preview", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ClaimSettlementView preview(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id,
                                       @Valid @RequestBody ClaimPreviewRequest request) {
        return service.previewForSeller(Long.parseLong(token.getSubject()), id, request.lines());
    }
}
