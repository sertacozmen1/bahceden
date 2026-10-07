package com.bahceden.common.web;

import org.springframework.http.HttpStatus;

/**
 * Beklenen, modellenmiş iş hatası (AGENTS.md §11.3).
 *
 * <p>Service katmanı iş kuralı ihlalini bu tiple fırlatır; HTTP gövdesine çevirme
 * işi tek bir yerde, {@link GlobalExceptionHandler} içinde yapılır. Durum kodu
 * hata koduna bağlıdır ({@link ApiErrorCode#status()}), çağıran seçmez: aynı
 * kod her uçta aynı durumla döner.
 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;

    /**
     * @param code    sabit hata kodu; HTTP durumu buradan türetilir
     * @param message kullanıcıya gösterilebilir Türkçe açıklama
     */
    public ApiException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }
}
