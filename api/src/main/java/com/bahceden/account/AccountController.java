package com.bahceden.account;

import com.bahceden.account.dto.AccountView;
import com.bahceden.account.dto.RegisterRequest;
import com.bahceden.account.dto.RegistrationAccepted;
import com.bahceden.account.dto.VerifyEmailRequest;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hesap uçları.
 *
 * <p>Controller incedir (AGENTS.md §11.2): doğrular, service'i çağırır, DTO
 * döner. Kayıt ve doğrulama uçları <strong>herkese açıktır</strong>, {@code me}
 * oturum ister; ikisi de {@code SecurityConfig} içinde açıkça işaretlidir.
 *
 * <p>Kayıt yanıtı her zaman <strong>202</strong> ve aynı gövdedir: e-postanın
 * sistemde olup olmadığı dışarıya sızmaz (design.md D4). İşin bir kısmı (e-posta
 * gönderimi) istek içinde yapılır ama sonucu yanıtı değiştirmez; 202 bu
 * "aldım, gerisini üstlendim" anlamını taşır.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Hesap", description = "Hesap açma, e-posta doğrulama ve oturum sahibinin kimliği")
public class AccountController {

    private final AccountService service;
    private final AccountMapper mapper;

    AccountController(AccountService service, AccountMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(
            operationId = "register",
            summary = "Hesap açar",
            description = """
                    Hesap doğrudan etkin açılır ve e-posta adresine bir doğrulama bağlantısı
                    gönderilir; doğrulama girişi engellemez. Yanıt, e-posta adresinin sistemde
                    kayıtlı olup olmadığını açığa çıkarmaz: her durumda aynı gövde döner.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "İstek alındı"),
            @ApiResponse(responseCode = "400", description = "E-posta veya parola geçersiz",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RegistrationAccepted register(@Valid @RequestBody RegisterRequest request) {
        service.register(request.email(), request.password());
        return RegistrationAccepted.standard();
    }

    @Operation(
            operationId = "verifyEmail",
            summary = "E-posta adresini doğrular",
            description = """
                    E-postadaki bağlantının token'ını tüketir ve hesabı doğrulanmış işaretler.
                    Token tek kullanımlıktır ve 24 saat geçerlidir. Doğrulama girişin koşulu
                    değildir; yalnızca hesabın bilgisini günceller.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-posta doğrulandı"),
            @ApiResponse(responseCode = "400",
                    description = "Token eksik (VALIDATION_FAILED) ya da geçersiz, kullanılmış veya süresi dolmuş (AUTH_TOKEN_INVALID)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(path = "/verifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        service.confirmVerification(request.token());
    }

    @Operation(
            operationId = "getCurrentAccount",
            summary = "Oturum sahibinin kimliğini döner",
            description = """
                    E-posta, roller ve e-posta doğrulama durumu güncel kayıttan okunur; rol
                    değişikliği erişim token'ının süresi dolmadan yansır.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum sahibinin kimliği"),
            @ApiResponse(responseCode = "401", description = "Erişim token'ı yok, geçersiz ya da süresi dolmuş (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountView currentAccount(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return mapper.toView(service.currentAccount(accountId(token)));
    }

    @Operation(
            operationId = "requestVerificationLink",
            summary = "Yeni doğrulama bağlantısı gönderir",
            description = """
                    Oturum sahibinin kayıtlı e-posta adresine yeni bir doğrulama bağlantısı
                    gönderir. Adres istekten alınmaz. E-posta zaten doğrulanmışsa bir şey
                    gönderilmez; yanıt yine aynıdır.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "İstek alındı"),
            @ApiResponse(responseCode = "401", description = "Erişim token'ı yok, geçersiz ya da süresi dolmuş (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(path = "/me/verification-requests")
    public void requestVerificationLink(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        service.requestVerificationLink(accountId(token));
    }

    /** Token'ın {@code sub} iddiası hesap kimliğidir (design.md D7). */
    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
