package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.order.dto.CartItemRequest;
import com.bahceden.order.dto.CartView;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum sahibinin sepeti (cart-and-checkout spec'i, add-cart-and-checkout D4).
 * Sepet token'daki hesaptan bulunur; istemci sepet kimliği göndermez.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Sepet", description = "Oturum sahibinin sepeti, satıcılara göre gruplu")
public class CartController {

    private final CartService service;

    CartController(CartService service) {
        this.service = service;
    }

    @Operation(operationId = "getCart", summary = "Sepeti döner",
            description = "Fiyat ve stok ürünün güncel değeridir; kargo ücreti ve toplamları sunucu hesaplar. Sepet yoksa boş döner.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Sepet")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CartView get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return service.view(accountId(token));
    }

    @Operation(operationId = "setCartItem", summary = "Ürünü sepete ekler veya adedini değiştirir",
            description = """
                    Adet 1–99 ve stoğu aşamaz. Satın alınamayan ürün ve satıcının kendi ürünü eklenemez; sepette en çok 50
                    farklı ürün olur.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Güncel sepet")
    @ApiResponse(responseCode = "400", description = "Adet geçersiz veya stoğu aşıyor (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Ürün yok (PRODUCT_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = """
            Ürün satın alınamaz (PRODUCT_NOT_PURCHASABLE), satıcının kendi ürünü (CANNOT_BUY_OWN_PRODUCT) veya sepet dolu
            (CART_FULL)""",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PutMapping(path = "/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartView setItem(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                            @PathVariable long productId, @Valid @RequestBody CartItemRequest request) {
        return service.setQuantity(accountId(token), productId, request.quantity());
    }

    @Operation(operationId = "removeCartItem", summary = "Ürünü sepetten çıkarır",
            description = "Satır yoksa sepet olduğu gibi döner.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Güncel sepet")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping(path = "/items/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CartView removeItem(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                               @PathVariable long productId) {
        return service.remove(accountId(token), productId);
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
