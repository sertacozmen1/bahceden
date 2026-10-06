package com.bahceden.account.domain;

/**
 * Hesap rolleri (ADR-008 §3).
 *
 * <p>Aynı hesap birden fazla rol taşıyabilir: üretici kendi de alışveriş yapar.
 * Spring Security'nin beklediği {@code ROLE_} önekiyle adlandırılır, böylece
 * yetkilendirme ifadelerinde dönüşüm gerekmez.
 *
 * <p>Kayıt olan herkes {@link #ROLE_CUSTOMER} alır. {@link #ROLE_SELLER} rolünü
 * veren akış satıcı onboarding kararına bağlıdır (KALANLAR A3) ve bu dilimde
 * yoktur; {@link #ROLE_ADMIN} yalnızca veritabanından verilir.
 */
public enum AccountRole {

    /** Alıcı: vitrinden alışveriş yapar. Kayıt olan herkesin varsayılan rolü. */
    ROLE_CUSTOMER,

    /** Satıcı: ürün yayınlar ve gönderir. Atama akışı KALANLAR A3'e bağlı. */
    ROLE_SELLER,

    /** Yönetici: operasyon ekranları. Yalnızca veritabanından verilir. */
    ROLE_ADMIN
}
