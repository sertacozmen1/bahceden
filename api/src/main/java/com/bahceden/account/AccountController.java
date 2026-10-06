package com.bahceden.account;

import com.bahceden.account.dto.RegisterRequest;
import com.bahceden.account.dto.RegistrationAccepted;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hesap uçları.
 *
 * <p>Controller incedir (AGENTS.md §11.2): doğrular, service'i çağırır, DTO
 * döner. Kayıt ucu <strong>herkese açıktır</strong> ve bu açıklık
 * {@code SecurityConfig} içinde açıkça işaretlidir.
 *
 * <p>Yanıt her zaman <strong>202</strong> ve aynı gövdedir: e-postanın sistemde
 * olup olmadığı dışarıya sızmaz (design.md D4). İşin bir kısmı (e-posta
 * gönderimi) istek içinde yapılır ama sonucu yanıtı değiştirmez; 202 bu
 * "aldım, gerisini üstlendim" anlamını taşır.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Hesap", description = "Hesap açma ve e-posta doğrulama")
public class AccountController {

    private final AccountService service;

    AccountController(AccountService service) {
        this.service = service;
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
}
