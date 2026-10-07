package com.bahceden.common.web;

import org.springframework.http.HttpStatus;

/**
 * Sabit hata kodları ve her birinin HTTP durumu.
 *
 * <p>İstemci bu kodlara göre dallanır; Türkçe mesaj değiştiğinde istemci
 * davranışı bozulmaz. Liste yalnızca gerçekten üretilen kodları içerir: ileride
 * kullanılabilecek kodlar şimdiden eklenmez (AGENTS.md K6).
 *
 * <p>Durum kodu koda bağlıdır: aynı kod her uçta aynı durumla döner (§11.5).
 */
public enum ApiErrorCode {

    /** İstek parametresi veya gövdesi doğrulamadan geçmedi (400). */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /**
     * E-posta veya parola hatalı (401). Hangisinin hatalı olduğu
     * <strong>söylenmez</strong>: hesabın var olup olmadığı sızmaz (design.md D4).
     */
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /**
     * Gönderilen tek kullanımlık token geçersiz, süresi dolmuş veya kullanılmış
     * (400). Üç durum ayrıştırılmaz: hangisi olduğu saldırgana bilgi verir,
     * kullanıcı için çözüm hep aynıdır (yeni bağlantı istemek).
     */
    AUTH_TOKEN_INVALID(HttpStatus.BAD_REQUEST),

    /**
     * Korunan uca erişim token'ı olmadan, geçersiz ya da süresi dolmuş bir
     * token'la gelindi (401). İstemci bu durumda oturumu bir kez yenilemeyi
     * dener (design.md D6).
     */
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),

    /** Beklenmeyen sunucu hatası (500); ayrıntı yalnızca sunucu log'unda. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
