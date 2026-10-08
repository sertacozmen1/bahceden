package com.bahceden.account.domain;

/**
 * Hesap rolleri (ADR-008 §3).
 *
 * <p>Aynı hesap birden fazla rol taşıyabilir: üretici kendi de alışveriş yapar.
 * Spring Security'nin beklediği {@code ROLE_} önekiyle adlandırılır, böylece
 * yetkilendirme ifadelerinde dönüşüm gerekmez.
 *
 * <p>Kayıt olan herkes {@link #ROLE_CUSTOMER} alır. {@link #ROLE_SELLER} satıcı
 * başvurusu onaylandığında eklenir, askıda kaldırılır (ADR-037,
 * add-seller-onboarding D5); {@link #ROLE_ADMIN} yalnızca veritabanından
 * verilir (D7).
 */
public enum AccountRole {

    /** Alıcı: vitrinden alışveriş yapar. Kayıt olan herkesin varsayılan rolü. */
    ROLE_CUSTOMER,

    /** Satıcı: ürün yayınlar ve gönderir. Başvurunun admin onayıyla verilir (ADR-037). */
    ROLE_SELLER,

    /** Yönetici: operasyon ekranları. Yalnızca veritabanından verilir. */
    ROLE_ADMIN
}
