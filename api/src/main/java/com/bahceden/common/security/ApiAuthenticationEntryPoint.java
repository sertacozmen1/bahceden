package com.bahceden.common.security;

import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Kimliksiz veya geçersiz token'lı isteğe 401 + ortak hata gövdesi döner.
 *
 * <p>Güvenlik filtresi {@code @RestControllerAdvice}'tan önce çalışır, bu
 * yüzden 401 gövdesi burada üretilir. Aksi halde korunan uçlar boş gövdeli
 * bir 401 dönerdi ve "tüm uçlarda tek hata gövdesi" kuralı (AGENTS.md §11.5)
 * kırılırdı.
 *
 * <p>Token'ın neden reddedildiği (süresi dolmuş, imza hatalı, biçim bozuk)
 * istemciye söylenmez: istemcinin yapacağı şey her durumda aynıdır (yenile,
 * olmazsa yeniden giriş).
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = "Bu işlem için giriş yapmanız gerekiyor.";

    private final ObjectMapper objectMapper;

    ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ApiErrorCode.AUTH_REQUIRED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // RFC 6750: Bearer şemasıyla korunan kaynak 401'de bunu bildirir.
        response.setHeader("WWW-Authenticate", "Bearer");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(ApiErrorCode.AUTH_REQUIRED, MESSAGE));
    }
}
