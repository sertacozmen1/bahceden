package com.bahceden.catalog.domain;

import com.bahceden.common.money.Money;
import com.bahceden.seller.domain.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Satılan ürün ilanı.
 *
 * <p>Vitrin yalnızca {@link ProductStatus#PUBLISHED} olanları gösterir
 * (storefront-catalog spec'i). Fiyat minor unit olarak {@link Money} içinde
 * saklanır (ADR-006); istemci fiyat hesaplamaz, yalnızca gösterir.
 *
 * <p>Entity veri taşır, iş kuralı taşımaz: yayına alma, stok düşme gibi
 * davranışlar ilgili service'te yaşar (AGENTS.md §11.2).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Üretici. Tembel yüklenir; listeleme sorgusu ihtiyaç duyduğu yerde
     * {@code join fetch} ile tek sorguda getirir (design.md D2).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false, length = 140, unique = true)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductUnit unit;

    /** Birim fiyat: {@code price_amount} ve {@code price_currency} kolonları. */
    @Embedded
    private Money price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    /**
     * Yayına alınma zamanı. Vitrin sıralaması buna dayanır; veritabanı kısıtı
     * yayınlanmış bir ürünün bu alanı boş bırakmasını engeller.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public Seller getSeller() {
        return seller;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public Money getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
