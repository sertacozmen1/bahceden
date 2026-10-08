package com.bahceden.catalog.domain;

/**
 * Ürün ilanının yayın durumu (design.md D1).
 *
 * <p>Boolean bir "aktif" bayrağı yerine enum kullanılır: satıcı ilanı hazırlayıp
 * sonra yayınlar ve ileride ön onay gibi dördüncü bir durum eklenebilir. İki
 * boolean bayrağı, geçersiz kombinasyonları (aktif ve arşiv) temsil edilebilir
 * yapardı.
 */
public enum ProductStatus {

    /** Satıcı hazırlıyor; vitrinde görünmez. */
    DRAFT,

    /** Vitrinde görünür ve satın alınabilir. */
    PUBLISHED,

    /** Satıcı sezonu kapattı; vitrinde görünmez, geçmiş siparişlerde kalır. Satıcı geri alabilir. */
    ARCHIVED,

    /**
     * Admin gerekçeyle kaldırdı (ADR-038 §3, add-product-listings D1); vitrinde
     * görünmez. Satıcı düzenleyebilir ama bu durumdan kendisi çıkamaz.
     */
    REMOVED
}
