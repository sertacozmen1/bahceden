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

    /**
     * İkinci faktör kodu hatalı, süresi dolmuş, kullanılmış ya da açık bir giriş
     * girişimi yok (401). Durumlar ayrıştırılmaz; kullanıcı için çözüm aynıdır:
     * kodu yeniden girmek veya yeniden giriş yapıp yeni kod almak (design.md D8).
     */
    AUTH_CODE_INVALID(HttpStatus.UNAUTHORIZED),

    /**
     * Kimlik geçerli ama rol yetmiyor (403): örneğin admin olmayan hesabın admin
     * uçlarını çağırması. Kaynağın var olup olmadığı söylenmez.
     */
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Oturum sahibinin satıcı kaydı yok, ya da istenen satıcı kaydı yok veya başkasına ait (404). */
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Hesabın zaten bir satıcı kaydı var; ikinci başvuru açılmaz (409, ADR-037). */
    SELLER_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Satıcı kaydının şimdiki durumundan bu geçiş yapılamaz (409, seller-onboarding spec'i). */
    SELLER_INVALID_TRANSITION(HttpStatus.CONFLICT),

    /** Başvuru için e-posta doğrulaması gerekir; karar e-postayla bildirilir (422, add-seller-onboarding D4). */
    SELLER_EMAIL_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * Satıcıya özgü işlem onaylı satıcı ister (403, add-seller-onboarding D5).
     * Rol token'da henüz duruyor olsa bile askıdaki satıcı bu kodu alır.
     */
    SELLER_NOT_APPROVED(HttpStatus.FORBIDDEN),

    /** Kısa sürede çok fazla istek ya da çok fazla hatalı kod denemesi (429, design.md D5). */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

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
