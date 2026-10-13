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

    /** İlan yok, başkasına ait ya da vitrinde görünmüyor (404, add-product-listings D2, D6). */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** İlanın şimdiki durumundan bu geçiş yapılamaz (409, add-product-listings D1). */
    PRODUCT_INVALID_TRANSITION(HttpStatus.CONFLICT),

    /** Vitrine çıkaran geçiş fotoğraf ister ya da vitrine çıkabilen ilanın son fotoğrafı silinemez (409, D1, D3). */
    PRODUCT_PHOTO_REQUIRED(HttpStatus.CONFLICT),

    /** İlan başına fotoğraf sınırı (5) doldu (409, D3). */
    PRODUCT_PHOTO_LIMIT_REACHED(HttpStatus.CONFLICT),

    /** Onaylı satıcının ödeme hesabı yok (404, add-cart-and-checkout D2). */
    PAYOUT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Ödeme sağlayıcısı alt üye kaydını reddetti; bilgiler saklandı, satış kapalı (422, D2). */
    PAYOUT_ACCOUNT_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Ödeme sağlayıcısına ulaşılamadı veya işlem reddedildi; kayıt değişmedi (502, D8, D9). */
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),

    /** Ürün şu an satın alınamaz: yayında değil, satıcı onaylı değil, ödeme hesabı etkin değil veya tükendi (422, D1). */
    PRODUCT_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Satıcı kendi ürününü sepete ekleyemez (422, D4). */
    CANNOT_BUY_OWN_PRODUCT(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Sepette en çok 50 farklı ürün olur (422, D4). */
    CART_FULL(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Adres yok veya başkasına ait (404, D5). */
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Hesap başına adres sınırı (10) doldu (422, D5). */
    ADDRESS_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Seçilen adresin iline bugün teslimat yok (422, D5). */
    ADDRESS_NOT_DELIVERABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Sepet boş; sipariş oluşturulamaz (422, D6). */
    CART_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Sepette satın alınamayan veya stoğu aşan satır var (422, D6). */
    CART_HAS_UNAVAILABLE_ITEMS(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Sipariş yok veya başkasına ait (404, D6). */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Satıcı siparişi yok, başkasına ait veya ödenmemiş (404, D6). */
    SELLER_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * Satıcı siparişi bu işlem için uygun durumda değil (409): iptal (D9), gönderi
     * açma veya teslim edilemeyen sipariş kararı (add-shipments D3, D7).
     */
    ORDER_INVALID_TRANSITION(HttpStatus.CONFLICT),

    /** Gönderi açmak için satıcının telefonu kayıtlı olmalı; entegratör çağrılmadı (422, add-shipments D3). */
    SHIPMENT_SENDER_PHONE_MISSING(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Kargo entegratörüne ulaşılamadı veya işlem reddedildi; kayıt değişmedi (502, add-shipments D3, D6). */
    SHIPPING_INTEGRATOR_ERROR(HttpStatus.BAD_GATEWAY),

    /** Satıcı siparişi için talep açılamaz: teslim edilmemiş ya da talebi zaten var (409, ADR-081). */
    CLAIM_NOT_ALLOWED(HttpStatus.CONFLICT),

    /** Teslimden bu yana talep süresi doldu (422, ADR-081). */
    CLAIM_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Talep veya fotoğrafı yok ya da hesap talebin tarafı değil (404, ADR-084). */
    CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Talebin şimdiki durumundan bu eylem yapılamaz (409, ADR-080). */
    CLAIM_INVALID_TRANSITION(HttpStatus.CONFLICT),

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
