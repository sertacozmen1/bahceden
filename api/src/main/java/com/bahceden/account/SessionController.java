package com.bahceden.account;

import com.bahceden.account.SessionService.Authenticated;
import com.bahceden.account.SessionService.ChallengeRequired;
import com.bahceden.account.dto.LoginChallengeIssued;
import com.bahceden.account.dto.LoginRequest;
import com.bahceden.account.dto.SessionTokens;
import com.bahceden.common.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum uçları (ADR-008).
 *
 * <p>Giriş iki farklı başarılı yanıt verir ve ikisi <strong>durum koduyla</strong>
 * ayrışır: {@code 200} token'lar (ikinci faktör kapalı), {@code 202} ikinci adım
 * bekleniyor (ikinci faktör açık, ADR-017). Tek bir gövdede boş bırakılabilen
 * alanlar yerine iki ayrı şema: istemci üretilen tiplerle iki durumu ayrı ele
 * alır, "token var mı?" kontrolü yazmaz.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Oturum", description = "Oturum açma")
public class SessionController {

    private final SessionService service;

    SessionController(SessionService service) {
        this.service = service;
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
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return switch (service.login(request.email(), request.password())) {
            case Authenticated session -> ResponseEntity.ok(new SessionTokens(
                    session.accessToken(),
                    session.accessTokenExpiresAt(),
                    session.refreshToken(),
                    session.refreshTokenExpiresAt()));
            case ChallengeRequired challenge -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(LoginChallengeIssued.until(challenge.expiresAt()));
        };
    }
}
