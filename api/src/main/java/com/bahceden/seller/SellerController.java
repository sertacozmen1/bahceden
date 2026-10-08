package com.bahceden.seller;

import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.seller.dto.SellerApplicationRequest;
import com.bahceden.seller.dto.SellerSelfView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum sahibinin satıcı kaydı: başvuru, yeniden gönderim, görüntüleme ve
 * belge (seller-onboarding spec'i, ADR-037).
 *
 * <p>Kaynak {@code /sellers/me}'dir ({@code /accounts/me} ile aynı desen):
 * kayıt token'daki hesaptan bulunur, istemci satıcı kimliği göndermez (ADR-007).
 * Uçlar oturum ister ama rol istemez: başvuran henüz satıcı değildir.
 */
@RestController
@RequestMapping("/api/v1/sellers/me")
@Tag(name = "Satıcı başvurusu", description = "Oturum sahibinin satıcı başvurusu ve kaydı")
public class SellerController {

    private static final String SELF_LOCATION = "/api/v1/sellers/me";

    private final SellerService service;
    private final RateLimiter rateLimiter;

    SellerController(SellerService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @Operation(
            operationId = "getOwnSeller",
            summary = "Oturum sahibinin satıcı kaydını döner",
            description = "Kayıt yoksa 404 döner; arayüz bunu başvuruya davet olarak gösterir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Satıcı kaydı"),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Hesabın satıcı kaydı yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerSelfView getOwn(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return service.findOwn(accountId(token));
    }

    @Operation(
            operationId = "applyAsSeller",
            summary = "Satıcı başvurusu yapar",
            description = """
                    Beyan alanları ve belge dosyası tek istekte gönderilir. Kayıt PENDING açılır ve
                    admin kararı e-postayla bildirilir. E-posta adresi doğrulanmış olmalıdır. Belge
                    türü içerikten doğrulanır: PDF, JPEG veya PNG; en çok 10 MB.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Başvuru alındı"),
            @ApiResponse(responseCode = "400", description = "Alan veya belge geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Hesabın zaten satıcı kaydı var (SELLER_ALREADY_EXISTS)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "E-posta doğrulanmamış (SELLER_EMAIL_NOT_VERIFIED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SellerSelfView> apply(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                                @Valid @ModelAttribute SellerApplicationRequest request) {
        var accountId = accountId(token);
        rateLimiter.check(RateLimitPolicy.SELLER_APPLICATION, Long.toString(accountId));
        return ResponseEntity.created(URI.create(SELF_LOCATION)).body(service.apply(accountId, request));
    }

    @Operation(
            operationId = "resubmitSellerApplication",
            summary = "Reddedilen başvuruyu yeniden gönderir",
            description = """
                    Yalnızca REJECTED durumundaki kayıt için: beyan güncellenir, yeni belge eklenir ve
                    kayıt yeniden PENDING olur. Önceki belge denetim izi için saklanır.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başvuru yeniden gönderildi"),
            @ApiResponse(responseCode = "400", description = "Alan veya belge geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Hesabın satıcı kaydı yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Kayıt reddedilmiş durumda değil (SELLER_INVALID_TRANSITION)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SellerSelfView resubmit(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                   @Valid @ModelAttribute SellerApplicationRequest request) {
        var accountId = accountId(token);
        rateLimiter.check(RateLimitPolicy.SELLER_APPLICATION, Long.toString(accountId));
        return service.resubmit(accountId, request);
    }

    @Operation(
            operationId = "getOwnSellerDocument",
            summary = "Oturum sahibinin güncel başvuru belgesini döner",
            description = """
                    Belge içerikten tespit edilen türüyle, tarayıcının içeriği yorumlamasını ve
                    betik çalıştırmasını engelleyen başlıklarla döner.
                    """,
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
            @ApiResponse(responseCode = "404", description = "Hesabın satıcı kaydı yok (SELLER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/document")
    public ResponseEntity<InputStreamResource> getOwnDocument(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return SellerDocumentResponses.inline(service.ownDocument(accountId(token)));
    }

    /** Token'ın {@code sub} iddiası hesap kimliğidir (add-account-and-session D7). */
    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
