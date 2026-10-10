package com.bahceden.shipping;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.ProtectedFileResponses;
import com.bahceden.shipping.dto.CreateShipmentRequest;
import com.bahceden.shipping.dto.ShipmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Satıcının gönderi uçları (shipments spec'i, add-shipments D3, D4).
 * {@code ROLE_SELLER} ister; otorite serviste denetlenen satıcı durumudur
 * (ADR-044). Etiket kişisel veri içerir ve korumalı başlıklarla sunulur.
 */
@RestController
@RequestMapping("/api/v1/sellers/me/orders/{id}/shipment")
@Tag(name = "Satıcı gönderileri", description = "Kargoya hazırla ve etiket")
public class SellerShipmentController {

    private static final String LABEL_NAME = "etiket";

    private final ShipmentService service;

    SellerShipmentController(ShipmentService service) {
        this.service = service;
    }

    @Operation(operationId = "createShipment", summary = "Satıcı siparişi için gönderi açar (kargoya hazırla)",
            description = """
                    Ödenmiş ve kargoya verilmemiş (NEW) satıcı siparişi için kargo entegratöründe gönderi açar;
                    paket şubeye teslim edilir. Gönderi zaten açıksa yenisi açılmaz, mevcut gönderi 200 ile döner.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "201", description = "Gönderi açıldı")
    @ApiResponse(responseCode = "200", description = "Gönderi zaten açıktı; mevcut gönderi")
    @ApiResponse(responseCode = "400", description = "Koli boyutu eksik veya geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Satıcı siparişi yok veya başkasına ait (SELLER_ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Satıcı siparişi kargoya hazırlanabilir durumda değil (ORDER_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Satıcının telefonu kayıtlı değil (SHIPMENT_SENDER_PHONE_MISSING)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Kargo entegratörüne ulaşılamadı; hiçbir şey değişmedi (SHIPPING_INTEGRATOR_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShipmentView> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                               @PathVariable long id, @Valid @RequestBody CreateShipmentRequest request) {
        var opened = service.open(accountId(token), id, request.packageSize());
        return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK).body(opened.shipment());
    }

    @Operation(operationId = "getShipmentLabel", summary = "Gönderinin etiketini döner",
            description = "Yalnızca satıcı siparişinin sahibine; PDF veya PNG, önbelleğe alınmaz.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Etiket",
            content = {@Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Gönderi yok veya sipariş başkasına ait (SELLER_ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/label")
    public ResponseEntity<InputStreamResource> label(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                                     @PathVariable long id) {
        var label = service.label(accountId(token), id);
        return ProtectedFileResponses.inline(label.type(), label.content().length,
                new ByteArrayInputStream(label.content()), LABEL_NAME);
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
