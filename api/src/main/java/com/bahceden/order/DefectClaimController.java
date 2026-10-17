package com.bahceden.order;

import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.order.dto.DefectClaimForm;
import com.bahceden.order.dto.OrderDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alıcının ayıplı ürün talebi (defect-claims spec'i; ADR-081, ADR-084).
 * Oturum ister; sipariş sorgusu alıcı kimliğiyle kurulur.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Ayıplı ürün talepleri", description = "Teslim edilen siparişte sorun bildirme ve çözüm")
public class DefectClaimController {

    private static final String SELF_PATH = "/api/v1/orders/";

    private final DefectClaimService service;
    private final RateLimiter rateLimiter;

    DefectClaimController(DefectClaimService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "openDefectClaim", summary = "Alıcı teslim edilen satıcı siparişi için ayıplı ürün talebi açar",
            description = """
                    Teslimden itibaren 48 saat içinde, satır ve adet, açıklama ve 1–3 fotoğrafla açılır. Satıcı
                    siparişi başına tek talep vardır. Fotoğraflar yeniden boyutlandırılır ve konum bilgisi atılır.
                    Satıcının 48 saatlik yanıt süresi başlar.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "201", description = "Talep açıldı; siparişin güncel hali")
    @ApiResponse(responseCode = "400", description = "Satır, adet, açıklama veya fotoğraf geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404",
            description = "Sipariş veya satıcı siparişi yok ya da başkasına ait (ORDER_NOT_FOUND, SELLER_ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409",
            description = "Satıcı siparişi teslim edilmemiş ya da talebi zaten var (CLAIM_NOT_ALLOWED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Teslimden bu yana talep süresi doldu (CLAIM_WINDOW_CLOSED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Kısa sürede çok fazla talep (RATE_LIMITED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{orderNumber}/seller-orders/{id}/claim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetail> open(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                            @PathVariable String orderNumber, @PathVariable long id,
                                            @Valid @ModelAttribute DefectClaimForm form) {
        var accountId = Long.parseLong(token.getSubject());
        rateLimiter.check(RateLimitPolicy.DEFECT_CLAIM, Long.toString(accountId));
        var detail = service.open(accountId, orderNumber, id, form);
        return ResponseEntity.created(URI.create(SELF_PATH + detail.orderNumber())).body(detail);
    }

    @Operation(operationId = "acceptDefectClaimAnswer", summary = "Alıcı satıcının teklifini veya reddini kabul eder",
            description = "Teklif kabul edilirse önerilen adetler iade edilir; ret kabul edilirse talep iadesiz kapanır.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Siparişin güncel hali")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Talep yok veya başkasının (CLAIM_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Talep alıcının yanıtını beklemiyor (CLAIM_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "İade yapılamadı; hiçbir şey değişmedi (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{orderNumber}/seller-orders/{id}/claim/accept", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderDetail accept(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                              @PathVariable String orderNumber, @PathVariable long id) {
        return service.acceptAsBuyer(Long.parseLong(token.getSubject()), orderNumber, id);
    }

    @Operation(operationId = "escalateDefectClaim", summary = "Alıcı satıcının teklifini veya reddini yönetime taşır",
            description = "Talep Bahçeden ekibinin kararına geçer; iki tarafa e-posta gider.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Siparişin güncel hali")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Talep yok veya başkasının (CLAIM_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Talep alıcının yanıtını beklemiyor (CLAIM_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{orderNumber}/seller-orders/{id}/claim/escalate", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderDetail escalate(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                @PathVariable String orderNumber, @PathVariable long id) {
        return service.escalateAsBuyer(Long.parseLong(token.getSubject()), orderNumber, id);
    }
}
