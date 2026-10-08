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
import java.util.Optional;

/**
 * Satılan ürün ilanı.
 *
 * <p>Vitrin yalnızca {@link ProductStatus#PUBLISHED} olanları gösterir
 * (storefront-catalog spec'i). Fiyat minor unit olarak {@link Money} içinde
 * saklanır (ADR-006); istemci fiyat hesaplamaz, yalnızca gösterir.
 *
 * <p>Durum yalnızca {@link #apply(ProductTransition, String, Instant)} ile, izinli
 * geçişlerden biri üzerinden değişir (add-product-listings D1). Kimin hangi
 * geçişi yapabileceği ve fotoğraf koşulu servistedir; entity yalnızca geçişin
 * şimdiki durumdan yapılabildiğini garanti eder.
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

    /** Admin kaldırma gerekçesi; satıcıya gösterilir (ADR-038 §3). */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    /** Yeni ilan: {@link ProductStatus#DRAFT} açılır, vitrinde görünmez. */
    public static Product draft(Seller seller, String slug, String name, String description, ProductUnit unit,
                                Money price, int stockQuantity, Instant now) {
        var product = new Product();
        product.seller = seller;
        product.slug = slug;
        product.status = ProductStatus.DRAFT;
        product.statusChangedAt = now;
        product.createdAt = now;
        product.update(name, description, unit, price, stockQuantity, now);
        return product;
    }

    /** İlan bilgilerini günceller; durum ve {@code slug} değişmez (D2). */
    public void update(String name, String description, ProductUnit unit, Money price, int stockQuantity,
                       Instant now) {
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.updatedAt = now;
    }

    /** Fotoğraf değişince ilanın son değişiklik anı güncellenir (satıcı paneli sıralaması). */
    public void touch(Instant now) {
        updatedAt = now;
    }

    public boolean allows(ProductTransition transition) {
        return transition.allowsFrom(status);
    }

    /**
     * Durumu bir geçişle değiştirir. İlk yayında {@code publishedAt} yazılır ve
     * sonra değişmez: aç-kapa ile vitrinde öne çıkılamaz (D1).
     *
     * @param reason kaldırma gerekçesi; gerekçe istemeyen geçişte önceki gerekçe temizlenir
     */
    public void apply(ProductTransition transition, String reason, Instant now) {
        if (!allows(transition)) {
            throw new IllegalStateException("İzinsiz ilan geçişi: " + status + " -> " + transition);
        }
        status = transition.to();
        statusReason = transition.requiresReason() ? reason : null;
        if (status == ProductStatus.PUBLISHED && publishedAt == null) {
            publishedAt = now;
        }
        statusChangedAt = now;
        updatedAt = now;
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

    public Optional<String> getStatusReason() {
        return Optional.ofNullable(statusReason);
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
