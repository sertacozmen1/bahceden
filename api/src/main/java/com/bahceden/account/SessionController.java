package com.bahceden.account;

import com.bahceden.account.SessionService.Authenticated;
import com.bahceden.account.SessionService.ChallengeRequired;
import com.bahceden.account.domain.Account;
import com.bahceden.account.dto.ChallengeRequest;
import com.bahceden.account.dto.LoginChallengeIssued;
import com.bahceden.account.dto.LoginRequest;
import com.bahceden.account.dto.RefreshTokenRequest;
import com.bahceden.account.dto.SessionTokens;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
import com.bahceden.common.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum uçları (ADR-008).
 *
 * <p>Giriş iki farklı başarılı yanıt verir ve ikisi <strong>durum koduyla</strong>
 * ayrışır: {@code 200} token'lar (ikinci faktör kapalı), {@code 202} ikinci adım
 * bekleniyor (ikinci faktör açık, ADR-017). Tek bir gövdede boş bırakılabilen
 * alanlar yerine iki ayrı şema: istemci üretilen tiplerle iki durumu ayrı ele
 * alır, "token var mı?" kontrolü yazmaz.
 *
 * <p>Hız sınırı burada uygulanır, çünkü istemci adresi HTTP katmanının
 * bilgisidir; service {@link HttpServletRequest} görmez (AGENTS.md §11.2).
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Oturum", description = "Oturum açma, yenileme ve kapatma")
public class SessionController {

    private final SessionService service;
    private final RateLimiter rateLimiter;
    private final Messages messages;

    SessionController(SessionService service, RateLimiter rateLimiter, Messages messages) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.messages = messages;
    }

    @Operation(
            operationId = "login",
            summary = "Oturum açar",
            description = """
                    E-posta ve parola ile oturum açar. E-posta doğrulaması girişin koşulu
                    değildir. Hesabın ikinci faktörü kapalıysa (varsayılan) token'lar döner;
                    açıksa token dönmez ve e-postaya tek kullanımlık bir kod gönderilir.
                    Hatalı e-posta ile hatalı parola aynı yanıtı alır.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum açıldı",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SessionTokens.class))),
            @ApiResponse(responseCode = "202", description = "İkinci adım bekleniyor; kod e-postayla gönderildi",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginChallengeIssued.class))),
            @ApiResponse(responseCode = "400", description = "İstek gövdesi geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "E-posta veya parola hatalı (AUTH_INVALID_CREDENTIALS)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                   @Parameter(hidden = true) Locale locale) {
        rateLimiter.check(RateLimitPolicy.LOGIN, http.getRemoteAddr(), Account.normalizeEmail(request.email()));
        // İsteğin dili hesabın dili olarak saklanır; giriş kodu bu dilde gider (ADR-032).
        var language = SupportedLocale.from(locale);
        return switch (service.login(request.email(), request.password(), language)) {
            case Authenticated session -> ResponseEntity.ok(toTokens(session));
            case ChallengeRequired challenge -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new LoginChallengeIssued(
                            messages.get("info.loginChallengeIssued", language), challenge.expiresAt()));
        };
    }

    @Operation(
            operationId = "completeLoginChallenge",
            summary = "İkinci faktör koduyla oturum açar",
            description = """
                    Girişin birinci adımı 202 döndüyse e-postaya gelen altı haneli kod burada
                    sunulur. Yalnızca son gönderilen kod geçerlidir; kod 10 dakika geçerlidir
                    ve bir kez kullanılabilir. Beş hatalı denemeden sonra girişim kilitlenir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum açıldı"),
            @ApiResponse(responseCode = "400", description = "İstek gövdesi geçersiz (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401",
                    description = "Kod hatalı, kullanılmış, süresi dolmuş ya da açık girişim yok (AUTH_CODE_INVALID)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla hatalı deneme (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(path = "/challenge", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SessionTokens completeChallenge(@Valid @RequestBody ChallengeRequest request, HttpServletRequest http) {
        rateLimiter.check(RateLimitPolicy.LOGIN_CHALLENGE, http.getRemoteAddr(),
                Account.normalizeEmail(request.email()));
        return toTokens(service.completeChallenge(request.email(), request.code()));
    }

    @Operation(
            operationId = "refreshSession",
            summary = "Oturumu yeniler",
            description = """
                    Yenileme token'ını tüketir; yeni bir erişim token'ı ve yeni bir yenileme
                    token'ı döner. Sunulan token bir daha kullanılamaz. Kullanılmış bir token
                    yeniden sunulursa oturum zincirinin tamamı iptal edilir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum yenilendi"),
            @ApiResponse(responseCode = "400",
                    description = "Token eksik (VALIDATION_FAILED) ya da geçersiz, kullanılmış, iptal edilmiş veya süresi dolmuş (AUTH_TOKEN_INVALID)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla istek (RATE_LIMITED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SessionTokens refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        rateLimiter.check(RateLimitPolicy.REFRESH, http.getRemoteAddr());
        return toTokens(service.refresh(request.refreshToken()));
    }

    @Operation(
            operationId = "logout",
            summary = "Oturumu kapatır",
            description = """
                    Yenileme token'ının ait olduğu oturum zincirinin tamamını iptal eder.
                    Erişim token'ı en fazla 15 dakika daha geçerli kalır. Bilinmeyen veya
                    zaten iptal edilmiş token için de 204 döner.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Oturum kapatıldı"),
            @ApiResponse(responseCode = "400", description = "Token eksik (VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(path = "/current", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
    }

    private static SessionTokens toTokens(Authenticated session) {
        return new SessionTokens(
                session.accessToken(),
                session.accessTokenExpiresAt(),
                session.refreshToken(),
                session.refreshTokenExpiresAt());
    }
}
