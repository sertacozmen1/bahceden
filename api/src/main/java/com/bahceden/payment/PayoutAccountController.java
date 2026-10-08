package com.bahceden.payment;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.payment.dto.PayoutAccountRequest;
import com.bahceden.payment.dto.PayoutAccountView;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Satıcının ödeme hesabı (seller-payout-details spec'i, add-cart-and-checkout D2).
 *
 * <p>{@code /sellers/me} deseni: satıcı token'daki hesaptan bulunur. Uçlar
 * {@code ROLE_SELLER} ister; otorite serviste denetlenen satıcı durumudur
 * (ADR-044).
 */
@RestController
@RequestMapping("/api/v1/sellers/me/payout-account")
@Tag(name = "Satıcı ödeme hesabı", description = "Onaylı satıcının ödeme bilgileri ve alt üye işyeri kaydı")
public class PayoutAccountController {

    private final PayoutAccountService service;

    PayoutAccountController(PayoutAccountService service) {
        this.service = service;
    }

    @Operation(operationId = "getOwnPayoutAccount", summary = "Satıcının ödeme hesabını döner",
            description = "IBAN ve kimlik numarası maskelidir. Hesap yoksa 404 döner.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PayoutResponses
    @ApiResponse(responseCode = "404", description = "Ödeme hesabı yok (PAYOUT_ACCOUNT_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PayoutAccountView get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return service.findOwn(accountId(token));
    }

    @Operation(operationId = "savePayoutAccount", summary = "Ödeme bilgilerini kaydeder veya günceller",
            description = """
                    Kimlik numarası ve IBAN sağlamalarıyla doğrulanır; kayıt aynı istekte ödeme sağlayıcısında alt üye
                    işyeri olarak açılır veya güncellenir. Sağlayıcı reddederse bilgiler saklanır, hesap
                    PROVIDER_REJECTED olur ve 422 döner.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PayoutResponses
    @ApiResponse(responseCode = "422", description = "Ödeme sağlayıcısı kaydı reddetti (PAYOUT_ACCOUNT_REJECTED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Ödeme sağlayıcısına ulaşılamadı (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PayoutAccountView save(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                  @Valid @RequestBody PayoutAccountRequest request) {
        return service.save(accountId(token), request);
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    /** Ödeme hesabı uçlarının ortak yanıtları. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödeme hesabı"),
            @ApiResponse(responseCode = "400", description = "Alan geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403",
                    description = "Satıcı rolü yok (AUTH_FORBIDDEN) veya satıcı kaydı onaylı değil (SELLER_NOT_APPROVED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @interface PayoutResponses {
    }
}
