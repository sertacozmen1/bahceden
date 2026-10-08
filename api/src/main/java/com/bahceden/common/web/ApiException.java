package com.bahceden.common.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Beklenen, modellenmiş iş hatası (AGENTS.md §11.3).
 *
 * <p>Service katmanı iş kuralı ihlalini bu tiple fırlatır; HTTP gövdesine çevirme
 * işi tek bir yerde, {@link GlobalExceptionHandler} içinde yapılır. Durum kodu
 * hata koduna bağlıdır ({@link ApiErrorCode#status()}), çağıran seçmez: aynı
 * kod her uçta aynı durumla döner.
 *
 * <p>İstisna metin değil <strong>mesaj anahtarı</strong> taşır (ADR-031): metin,
 * gövde üretilirken isteğin dilinde çözülür. Servis katmanı dil bilmez.
 */
public class ApiException extends RuntimeException {

    /**
     * Servis katmanında saptanan alan hatası (örneğin dosya türü): alan adı ve
     * o alanın mesaj anahtarı. Metin, gövde üretilirken isteğin dilinde çözülür.
     *
     * @param field      hatalı alanın adı
     * @param messageKey alan mesajının anahtarı
     */
    public record FieldErrorKey(String field, String messageKey) {
    }

    private final ApiErrorCode code;
    private final String messageKey;
    private final List<FieldErrorKey> fieldErrors;

    /**
     * @param code       sabit hata kodu; HTTP durumu buradan türetilir
     * @param messageKey {@code messages.properties} anahtarı (örn. {@code error.invalidCredentials})
     */
    public ApiException(ApiErrorCode code, String messageKey) {
        // Log'larda anahtar görünür; kullanıcı metni ve kişisel veri değil.
        this(code, messageKey, List.of());
    }

    /**
     * Bean Validation'ın göremediği, servis katmanında saptanan alan hataları
     * için (dosya türü ve boyutu, telefon biçimi).
     */
    public ApiException(ApiErrorCode code, String messageKey, List<FieldErrorKey> fieldErrors) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** Tek alanlı doğrulama hatası kısayolu: {@code VALIDATION_FAILED}. */
    public static ApiException invalidField(String field, String fieldMessageKey) {
        return new ApiException(ApiErrorCode.VALIDATION_FAILED, "error.validation",
                List.of(new FieldErrorKey(field, fieldMessageKey)));
    }

    public List<FieldErrorKey> getFieldErrors() {
        return fieldErrors;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public HttpStatus getStatus() {
        return code.status();
    }
}
