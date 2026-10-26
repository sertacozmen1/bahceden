package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.dto.CancellationRequest;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.order.dto.SellerOrderSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Optional;
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
 * Satıcının gelen siparişleri (orders spec'i, add-cart-and-checkout D6).
 * {@code ROLE_SELLER} ister; otorite serviste denetlenen satıcı durumudur
 * (ADR-044). Alıcının adresinden yalnızca il ve ilçe döner.
 */
@RestController
@Validated
@RequestMapping("/api/v1/sellers/me/orders")
@Tag(name = "Satıcı siparişleri", description = "Onaylı satıcının gelen siparişleri")
public class SellerOrderController {

    private final OrderService service;
    private final OrderCancellationService cancellation;

    SellerOrderController(OrderService service, OrderCancellationService cancellation) {
        this.service = service;
        this.cancellation = cancellation;
    }

    @Operation(operationId = "listSellerOrders", summary = "Satıcının gelen siparişlerini listeler",
            description = """
                    Yalnızca ödemesi tamamlanmış satıcı siparişleri; en yeni üstte. Durumla süzülebilir
                    (NEW, SHIPPED, DELIVERED, DELIVERY_FAILED, REFUNDED, CLOSED, CANCELLED).
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Satıcı siparişleri")
    @ApiResponse(responseCode = "400", description = "Süzgeç veya sayfalama değeri geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<SellerOrderSummary> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(description = "Durum süzgeci; boşsa ödenmiş satıcı siparişlerinin hepsi", example = "NEW")
            @RequestParam(required = false) SellerOrderStatus status,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listForSeller(accountId(token), Optional.ofNullable(status), page, size);
    }

    @Operation(operationId = "getSellerOrder", summary = "Satıcının tek satıcı siparişini döner",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Satıcı siparişi")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Satıcı siparişi yok veya başkasına ait (SELLER_ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerOrderDetail get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return service.detailForSeller(accountId(token), id);
    }

    @Operation(operationId = "cancelSellerOrderAsSeller", summary = "Satıcı, kargoya vermediği siparişi gerekçeyle iptal eder",
            description = """
                    Gerekçe zorunludur (10–500 karakter) ve alıcıya e-postayla iletilir. Ürün tutarı ve kargo ücreti
                    alıcıya iade edilir, stok geri eklenir.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Satıcı siparişinin güncel hali")
    @ApiResponse(responseCode = "400", description = "Gerekçe eksik veya geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Satıcı siparişi yok veya başkasına ait (SELLER_ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Satıcı siparişi iptal edilebilir durumda değil: iptal edilmiş veya kargoya verilmiş (ORDER_INVALID_TRANSITION); ya da iptal reddedildi ve paketin kargoya verildiği okundu, gönderi ve sipariş güncellendi (ORDER_ALREADY_SHIPPED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Gönderi iptal edilemedi (SHIPPING_INTEGRATOR_ERROR) veya iade yapılamadı (PAYMENT_PROVIDER_ERROR); hiçbir şey değişmedi",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerOrderDetail cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id,
                                    @Valid @RequestBody CancellationRequest request) {
        return cancellation.cancelBySeller(accountId(token), id, request.reason());
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
