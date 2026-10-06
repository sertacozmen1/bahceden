package com.bahceden.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Ürünü üreten ve gönderen üretici.
 *
 * <p>Bu dilimde bilinçli olarak <strong>minimum</strong>dur (design.md D4):
 * vitrinin ürünü üreticisiyle göstermesi için gereken ad ve şehirden ibarettir.
 * Hesap ilişkisi, vergi/belge alanları ve onay durumu satıcı onboarding
 * kararıyla (KALANLAR A3) ayrı bir migration ile gelecektir.
 */
@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL'de kullanılan kalıcı, okunabilir tanımlayıcı. */
    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    /** Vitrinde gösterilen ad. */
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    /** Ürünün hangi yöreden geldiğini göstermek için. */
    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Seller() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCity() {
        return city;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
