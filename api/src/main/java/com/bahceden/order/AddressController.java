package com.bahceden.order;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.dto.AddressRequest;
import com.bahceden.order.dto.AddressView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum sahibinin teslimat adresleri (cart-and-checkout spec'i, D5).
 * Başkasının adresi 404 döner; varlığı açığa çıkmaz (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Adresler", description = "Oturum sahibinin teslimat adresleri")
public class AddressController {

    private static final String SELF_PATH = "/api/v1/addresses/";

    private final AddressService service;

    AddressController(AddressService service) {
        this.service = service;
    }

    @Operation(operationId = "listAddresses", summary = "Adresleri listeler",
            description = "En çok 10 adres; tamamı tek sayfada döner.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Adresler")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<AddressView> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return service.list(accountId(token));
    }

    @Operation(operationId = "createAddress", summary = "Adres ekler",
            description = "İl teslimat yapılan illerden biri olmalıdır; hesap başına en çok 10 adres.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AddressResponses
    @ApiResponse(responseCode = "201", description = "Adres eklendi; Location başlığı adresin adresi")
    @ApiResponse(responseCode = "422", description = "Adres sınırı doldu (ADDRESS_LIMIT_REACHED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddressView> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                              @Valid @RequestBody AddressRequest request) {
        var created = service.create(accountId(token), request);
        return ResponseEntity.created(URI.create(SELF_PATH + created.id())).body(created);
    }

    @Operation(operationId = "updateAddress", summary = "Adresi günceller",
            description = "Geçmiş siparişler etkilenmez; sipariş adresin kopyasını taşır.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @AddressResponses
    @ApiResponse(responseCode = "404", description = "Adres yok veya başkasına ait (ADDRESS_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddressView update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                              @PathVariable long id, @Valid @RequestBody AddressRequest request) {
        return service.update(accountId(token), id, request);
    }

    @Operation(operationId = "deleteAddress", summary = "Adresi siler",
            description = "Geçmiş siparişler etkilenmez.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "204", description = "Adres silindi")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Adres yok veya başkasına ait (ADDRESS_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                       @PathVariable long id) {
        service.delete(accountId(token), id);
        return ResponseEntity.noContent().build();
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    /** Adres yazma uçlarının ortak yanıtları. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres"),
            @ApiResponse(responseCode = "400", description = "Alan geçersiz veya il teslimat listesinde yok (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface AddressResponses {
    }
}
