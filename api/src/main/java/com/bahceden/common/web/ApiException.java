package com.bahceden.common.web;

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

    private final ApiErrorCode code;
    private final String messageKey;

    /**
     * @param code       sabit hata kodu; HTTP durumu buradan türetilir
     * @param messageKey {@code messages.properties} anahtarı (örn. {@code error.invalidCredentials})
     */
    public ApiException(ApiErrorCode code, String messageKey) {
        // Log'larda anahtar görünür; kullanıcı metni ve kişisel veri değil.
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
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
