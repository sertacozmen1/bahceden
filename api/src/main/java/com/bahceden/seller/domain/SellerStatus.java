package com.bahceden.seller.domain;

/**
 * Satıcı kaydının durumu (ADR-037, design.md D1).
 *
 * <p>Başvuru ayrı bir varlık değil, satıcı kaydının {@link #PENDING} hâlidir.
 * Durumlar arası izinli geçişler {@link SellerTransition} içinde tek yerde
 * tanımlıdır; bu enum yalnızca durumları adlandırır.
 */
public enum SellerStatus {

    /** Başvuru yapıldı veya yeniden gönderildi; admin kararı bekleniyor. */
    PENDING,

    /** Onaylı: hesabın {@code ROLE_SELLER} rolü var, ürünleri vitrinde görünür (ADR-038). */
    APPROVED,

    /** Gerekçeyle reddedildi; sahibi düzeltip yeniden gönderebilir. */
    REJECTED,

    /** Onaylıyken gerekçeyle askıya alındı; rol kaldırıldı, ürünleri vitrinden düştü. */
    SUSPENDED
}
