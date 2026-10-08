package com.bahceden.catalog.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * İlanın izinli durum geçişleri: tek yerde, adlandırılmış (add-product-listings
 * D1, product-listings spec'i).
 *
 * <p>Satıcı kaydındaki {@code SellerTransition} deseni: geçiş eylemle
 * tanımlanır, çünkü aynı hedefe farklı eylemlerle ve farklı koşullarla gidilir
 * (yayına alma ile yeniden açma ikisi de {@link ProductStatus#PUBLISHED}).
 * Kimin yapabileceği ({@link Actor}) ve koşullar (fotoğraf, gerekçe) burada
 * sabittir.
 */
public enum ProductTransition {

    PUBLISH(Actor.SELLER, EnumSet.of(ProductStatus.DRAFT), ProductStatus.PUBLISHED, true, false),

    UNPUBLISH(Actor.SELLER, EnumSet.of(ProductStatus.PUBLISHED), ProductStatus.DRAFT, false, false),

    ARCHIVE(Actor.SELLER, EnumSet.of(ProductStatus.DRAFT, ProductStatus.PUBLISHED), ProductStatus.ARCHIVED, false, false),

    RESTORE(Actor.SELLER, EnumSet.of(ProductStatus.ARCHIVED), ProductStatus.DRAFT, false, false),

    REMOVE(Actor.ADMIN, EnumSet.of(ProductStatus.DRAFT, ProductStatus.PUBLISHED), ProductStatus.REMOVED, false, true),

    REINSTATE(Actor.ADMIN, EnumSet.of(ProductStatus.REMOVED), ProductStatus.PUBLISHED, true, false);

    /** Geçişi kim yapar: satıcı uçları admin geçişlerini, admin uçları satıcı geçişlerini kabul etmez. */
    public enum Actor {
        SELLER,
        ADMIN
    }

    private final Actor actor;
    private final Set<ProductStatus> from;
    private final ProductStatus to;
    private final boolean requiresPhoto;
    private final boolean requiresReason;

    ProductTransition(Actor actor, Set<ProductStatus> from, ProductStatus to, boolean requiresPhoto,
                      boolean requiresReason) {
        this.actor = actor;
        this.from = from;
        this.to = to;
        this.requiresPhoto = requiresPhoto;
        this.requiresReason = requiresReason;
    }

    public Actor actor() {
        return actor;
    }

    public boolean allowsFrom(ProductStatus status) {
        return from.contains(status);
    }

    public ProductStatus to() {
        return to;
    }

    /** Vitrine çıkaran geçişler en az bir fotoğraf ister. */
    public boolean requiresPhoto() {
        return requiresPhoto;
    }

    /** Kaldırma gerekçe ister; gerekçe satıcıya gösterilir. */
    public boolean requiresReason() {
        return requiresReason;
    }
}
