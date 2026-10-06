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

    /** Sezonu kapanmış veya kaldırılmış; vitrinde görünmez, geçmiş siparişlerde kalır. */
    ARCHIVED
}
