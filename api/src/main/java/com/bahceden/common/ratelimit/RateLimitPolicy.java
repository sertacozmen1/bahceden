package com.bahceden.common.ratelimit;

import java.time.Duration;

/**
 * Hız sınırı politikaları: her biri bir uç ailesi için pencere başına izin
 * verilen istek sayısı (design.md D5).
 *
 * <p>Değerler tek yerde durur; sihirli sayı uçlara dağılmaz (AGENTS.md §11.3).
 * Sınırlar, gerçek bir kullanıcının takılmayacağı ama kaba kuvvetin anlamsız
 * kalacağı aralıkta seçildi; ölçüm geldikçe burada ayarlanır.
 */
public enum RateLimitPolicy {

    /** Hesap açma: aynı istemciden saatte 10 kayıt. */
    REGISTRATION(10, Duration.ofHours(1)),

    /** Giriş: aynı istemci + e-posta için 15 dakikada 10 deneme. */
    LOGIN(10, Duration.ofMinutes(15)),

    /** İkinci adım: aynı istemci + e-posta için 15 dakikada 10 deneme (girişim başına ayrıca 5 hatalı kod). */
    LOGIN_CHALLENGE(10, Duration.ofMinutes(15)),

    /** Doğrulama bağlantısının tüketilmesi: aynı istemciden 15 dakikada 20. */
    EMAIL_VERIFICATION(20, Duration.ofMinutes(15)),

    /** Yeni doğrulama bağlantısı: aynı hesap için saatte 3 e-posta (design.md D9). */
    VERIFICATION_REQUEST(3, Duration.ofHours(1)),

    /** Oturum yenileme: aynı istemciden 15 dakikada 60. */
    REFRESH(60, Duration.ofMinutes(15)),

    /** Parola sıfırlama talebi: aynı istemciden saatte 10; tek kaynaktan adres taraması yavaşlar (ADR-034). */
    PASSWORD_RESET_REQUEST(10, Duration.ofHours(1)),

    /** Parola sıfırlama talebi: aynı e-posta için saatte 3; kurbanın gelen kutusu doldurulamaz (ADR-034). */
    PASSWORD_RESET_REQUEST_EMAIL(3, Duration.ofHours(1)),

    /** Sıfırlama bağlantısının tüketilmesi: aynı istemciden 15 dakikada 20. */
    PASSWORD_RESET(20, Duration.ofMinutes(15)),

    /** Satıcı başvurusu veya yeniden gönderim: aynı hesap için saatte 5; her biri bir dosya yazar (add-seller-onboarding). */
    SELLER_APPLICATION(5, Duration.ofHours(1)),

    /** İlan fotoğrafı yükleme: aynı hesap için saatte 60; her biri görüntü işler (add-product-listings D3). */
    PRODUCT_PHOTO_UPLOAD(60, Duration.ofHours(1));

    private final int limit;
    private final Duration window;

    RateLimitPolicy(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }
}
