package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.dto.AdminSellerOrderDetail;
import com.bahceden.order.dto.AdminSellerOrderSummary;
import com.bahceden.order.dto.UndeliveredResolutionRequest;
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
 * Admin: teslim edilemeyen satıcı siparişleri (shipments spec'i "Teslim
 * edilemeyen siparişte admin karar verir", add-shipments D7). Bütün uçlar
 * {@code ROLE_ADMIN} ister ({@code SecurityConfig}).
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/seller-orders")
@Tag(name = "Admin: siparişler", description = "Teslim edilemeyen satıcı siparişleri ve karar")
public class AdminSellerOrderController {

    private final UndeliveredOrderService service;

    AdminSellerOrderController(UndeliveredOrderService service) {
        this.service = service;
    }

    @Operation(operationId = "listSellerOrdersForAdmin", summary = "Teslim edilemeyen satıcı siparişlerini listeler",
            description = """
                    Durum süzgeci DELIVERY_FAILED (karar bekleyen, varsayılan), REFUNDED veya CLOSED olabilir;
                    en yeni üstte. Sayfa boyutunun üst sınırı sunucuda 50'dir.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminOrderResponses
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<AdminSellerOrderSummary> list(
            @Parameter(description = "Durum süzgeci", example = "DELIVERY_FAILED")
            @RequestParam(defaultValue = "DELIVERY_FAILED") SellerOrderStatus status,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.list(status, page, size);
    }

    @Operation(operationId = "getSellerOrderForAdmin", summary = "Teslim edilemeyen satıcı siparişinin detayı",
            description = "Alıcının adı ve telefonu iletişim için görünür; açık adres gösterilmez.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminOrderResponses
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerOrderDetail get(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(operationId = "resolveUndeliveredSellerOrder", summary = "Teslim edilemeyen satıcı siparişine karar verir",
            description = """
                    REFUND: ürün tutarı ve kargo ücreti alıcıya iade edilir, stok geri eklenmez, durum REFUNDED olur.
                    CLOSE: gerekçe zorunludur (10–500 karakter), iade yapılmaz, durum CLOSED olur.
                    Alıcıya ve satıcıya e-posta gider.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AdminOrderResponses
    @ApiResponse(responseCode = "409", description = "Satıcı siparişi karar bekliyor değil (ORDER_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "İade yapılamadı; hiçbir şey değişmedi (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/resolution", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminSellerOrderDetail resolve(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                          @PathVariable long id, @Valid @RequestBody UndeliveredResolutionRequest request) {
        return service.resolve(Long.parseLong(token.getSubject()), id, request.decision(), request.reason());
    }

    /** Admin sipariş uçlarının ortak yanıtları. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Satıcı siparişi veya siparişleri"),
            @ApiResponse(responseCode = "400", description = "Karar, gerekçe veya süzgeç geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Admin rolü yok (AUTH_FORBIDDEN)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Satıcı siparişi yok veya teslim edilemeyenlerden değil (SELLER_ORDER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface AdminOrderResponses {
    }
}
