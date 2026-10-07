package com.bahceden.common.web;

import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tüm hataları {@link ApiError} gövdesine çeviren tek nokta (AGENTS.md §11.5).
 *
 * <p>Her uç kendi hata biçimini üretmez: istemci tek bir sözleşmeye göre hata
 * işler. Beklenmeyen hatalar istemciye genel bir mesajla döner, ayrıntı yalnızca
 * sunucu log'una yazılır; iç sınıf adı ve SQL dışarıya sızmaz.
 *
 * <p>Metinler isteğin dilinde çözülür (ADR-031): {@code Locale} parametresini
 * Spring {@code Accept-Language}'tan doldurur. Alan doğrulama mesajları
 * doğrulama sırasında zaten aynı dilde üretilmiştir. Hata {@code code}
 * değerleri dilden bağımsızdır.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;

    GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    /** İstek gövdesi doğrulaması (DTO üzerindeki Bean Validation kısıtları). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException exception, Locale locale) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, text("error.validation", locale), fieldErrors));
    }

    /** İstek parametresi doğrulaması (controller imzasındaki kısıtlar). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException exception,
                                                              Locale locale) {
        var fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, text("error.validation", locale), fieldErrors));
    }

    /** Tip uyuşmazlığı: sayı beklenen parametreye metin gönderilmesi gibi. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception, Locale locale) {
        var fieldError = new ApiError.FieldError(exception.getName(), text("error.typeMismatch", locale));
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, text("error.validation", locale), List.of(fieldError)));
    }

    /** Modellenmiş iş hatası: kod ve durum {@link ApiErrorCode} içinde sabittir. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception, Locale locale) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getCode(), text(exception.getMessageKey(), locale)));
    }

    /**
     * Beklenmeyen hata. İstemciye yalnızca genel bir mesaj döner; teşhis için
     * gereken her şey sunucu log'una yazılır.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, Locale locale) {
        log.error("Beklenmeyen hata", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiErrorCode.INTERNAL_ERROR, text("error.internal", locale)));
    }

    private String text(String key, Locale locale) {
        return messages.get(key, SupportedLocale.from(locale));
    }
}
