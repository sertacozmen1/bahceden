package com.bahceden.common.security;

import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Kimliği geçerli ama rolü yetmeyen isteğe 403 ve ortak hata gövdesi döner
 * (AGENTS.md §11.5).
 *
 * <p>{@link ApiAuthenticationEntryPoint}'in 403 karşılığı: güvenlik filtresi
 * {@code @RestControllerAdvice}'tan önce çalıştığı için gövde burada üretilir.
 * Aksi halde admin uçları rolsüz istekte boş gövdeli bir 403 dönerdi. İlk 403
 * üreten uçlar satıcı başvurusunun admin uçlarıdır (add-seller-onboarding D7).
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Messages messages;
    private final LocaleResolver localeResolver;

    ApiAccessDeniedHandler(ObjectMapper objectMapper, Messages messages, LocaleResolver localeResolver) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.localeResolver = localeResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ApiErrorCode.AUTH_FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var locale = SupportedLocale.from(localeResolver.resolveLocale(request));
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ApiErrorCode.AUTH_FORBIDDEN, messages.get("error.forbidden", locale)));
    }
}
