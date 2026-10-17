package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.dto.AccountView;
import com.bahceden.account.dto.PasswordChangeRequest;
import com.bahceden.account.dto.PasswordResetConfirmation;
import com.bahceden.account.dto.PasswordResetRequest;
import com.bahceden.account.dto.PasswordResetRequested;
import com.bahceden.account.dto.RegisterRequest;
import com.bahceden.account.dto.RegistrationAccepted;
import com.bahceden.account.dto.VerifyEmailRequest;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
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
 * <p>Parola sıfırlama uçları da herkese açıktır: talep, kayıt gibi her durumda
 * aynı yanıtı döner (ADR-034); onay oturum açmaz (ADR-035).
 *
 * <p>Kayıt yanıtı her zaman <strong>202</strong> ve aynı gövdedir: e-postanın
 * sistemde olup olmadığı dışarıya sızmaz (design.md D4). İşin bir kısmı (e-posta
 * gönderimi) istek içinde yapılır ama sonucu yanıtı değiştirmez; 202 bu
 * "aldım, gerisini üstlendim" anlamını taşır.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Hesap", description = "Hesap açma, e-posta doğrulama, parola sıfırlama ve oturum sahibinin kimliği")
public class AccountController {

    private final AccountService service;
    private final AccountMapper mapper;
    private final RateLimiter rateLimiter;
    private final Messages messages;

    AccountController(AccountService service, AccountMapper mapper, RateLimiter rateLimiter, Messages messages) {
        this.service = service;
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
        this.messages = messages;
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
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RegistrationAccepted register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
                                         @Parameter(hidden = true) Locale locale) {
        rateLimiter.check(RateLimitPolicy.REGISTRATION, http.getRemoteAddr());
        var language = SupportedLocale.from(locale);
        service.register(request.email(), request.password(), language);
        // Tüm kayıt sonuçları için tek mesaj (D4); dil isteğin dilidir (ADR-031).
        return new RegistrationAccepted(messages.get("info.registrationAccepted", language));
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
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(path = "/verifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        rateLimiter.check(RateLimitPolicy.EMAIL_VERIFICATION, http.getRemoteAddr());
        service.confirmVerification(request.token());
    }

    @Operation(
            operationId = "requestPasswordReset",
            summary = "Parola sıfırlama bağlantısı gönderir",
            description = """
                    Adres kayıtlı bir hesaba aitse hesabın dilinde, 1 saat geçerli ve tek
                    kullanımlık bir sıfırlama bağlantısı gönderir; önceki bağlantılar geçersiz
                    olur. Yanıt, adresin kayıtlı olup olmadığını açığa çıkarmaz: her durumda
                    aynı gövde döner. Kayıtsız adrese e-posta gönderilmez.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "İstek alındı"),
            @ApiResponse(responseCode = "400", description = "E-posta geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(path = "/password-resets",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PasswordResetRequested requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                       HttpServletRequest http,
                                                       @Parameter(hidden = true) Locale locale) {
        rateLimiter.check(RateLimitPolicy.PASSWORD_RESET_REQUEST, http.getRemoteAddr());
        rateLimiter.check(RateLimitPolicy.PASSWORD_RESET_REQUEST_EMAIL, Account.normalizeEmail(request.email()));
        service.requestPasswordReset(request.email());
        // Tüm sonuçlar için tek mesaj (ADR-034); dil isteğin dilidir (ADR-031).
        return new PasswordResetRequested(messages.get("info.passwordResetRequested", SupportedLocale.from(locale)));
    }

    @Operation(
            operationId = "resetPassword",
            summary = "Sıfırlama bağlantısıyla yeni parola belirler",
            description = """
                    Sıfırlama token'ını tüketir ve parolayı değiştirir. Hesabın bütün oturumları
                    ve açık giriş kodları kapanır, e-posta doğrulanmış işaretlenir ve parolanın
                    değiştiği hesabın e-posta adresine bildirilir. Oturum açılmaz: kullanıcı
                    yeni parolasıyla giriş yapar.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Parola değişti"),
            @ApiResponse(responseCode = "400",
                    description = "Token veya parola geçersiz (VALIDATION_FAILED) ya da token geçersiz, kullanılmış veya süresi dolmuş (AUTH_TOKEN_INVALID)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(path = "/password-resets/confirmations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void resetPassword(@Valid @RequestBody PasswordResetConfirmation request, HttpServletRequest http) {
        rateLimiter.check(RateLimitPolicy.PASSWORD_RESET, http.getRemoteAddr());
        service.resetPassword(request.token(), request.password());
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
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(path = "/me/verification-requests")
    public void requestVerificationLink(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        var accountId = accountId(token);
        rateLimiter.check(RateLimitPolicy.VERIFICATION_REQUEST, Long.toString(accountId));
        service.requestVerificationLink(accountId);
    }

    @Operation(
            operationId = "changePassword",
            summary = "Oturum açıkken parolayı değiştirir",
            description = """
                    Mevcut parolayı doğrular ve yeni parolayı belirler. Hesabın bütün oturumları,
                    bu istek yapan oturum dahil, ve açık giriş kodları kapanır; parolanın değiştiği
                    hesabın e-posta adresine bildirilir. Oturum açılmaz: kullanıcı yeni parolasıyla
                    giriş yapar. Mevcut parola yanlışsa ya da yeni parola mevcutla aynıysa ilgili
                    alanda hata döner ve hiçbir şey değişmez. Hesap başına 15 dakikada 5 istek.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Parola değişti, oturumlar kapandı"),
            @ApiResponse(responseCode = "400",
                    description = "Alan eksik, yeni parola kurallara uymuyor, mevcut parola yanlış ya da yeni parola mevcutla aynı (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Erişim token'ı yok, geçersiz ya da süresi dolmuş (AUTH_REQUIRED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(path = "/me/password-changes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        var accountId = accountId(token);
        // Sınır parola denetiminden önce: çalınmış oturumla kaba kuvvet durur (D2).
        rateLimiter.check(RateLimitPolicy.PASSWORD_CHANGE, Long.toString(accountId));
        service.changePassword(accountId, request.currentPassword(), request.newPassword());
    }

    /** Token'ın {@code sub} iddiası hesap kimliğidir (design.md D7). */
    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
