package com.bahceden.common.web;

import java.util.List;
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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_MESSAGE = "Gönderilen değerler geçersiz.";

    /** İstek gövdesi doğrulaması (DTO üzerindeki Bean Validation kısıtları). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException exception) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, fieldErrors));
    }

    /** İstek parametresi doğrulaması (controller imzasındaki kısıtlar). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException exception) {
        var fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, fieldErrors));
    }

    /** Tip uyuşmazlığı: sayı beklenen parametreye metin gönderilmesi gibi. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        var fieldError = new ApiError.FieldError(exception.getName(), "Değer beklenen türde değil.");
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, List.of(fieldError)));
    }

    /**
     * Beklenmeyen hata. İstemciye yalnızca genel bir mesaj döner; teşhis için
     * gereken her şey sunucu log'una yazılır.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Beklenmeyen hata", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiErrorCode.INTERNAL_ERROR,
                        "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin."));
    }
}
