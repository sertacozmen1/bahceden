package com.bahceden.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * İlan fotoğrafı (add-product-listings D3, D4). Kapak {@code position = 0}
 * olan satırdır; ayrı bir kapak alanı yoktur.
 *
 * <p>Dosyaların kendisi depolama dizinindedir (ADR-039); burada iki boyutun
 * sunucunun ürettiği anahtarları ve büyük boyutun ölçüleri durur. Orijinal
 * dosya saklanmaz. Anahtarlar değişmez: fotoğraf değiştirilince yeni satır ve
 * yeni anahtarlar oluşur, bu yüzden uzun önbellek güvenlidir (D5).
 */
@Entity
@Table(name = "product_photos")
public class ProductPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int position;

    @Column(name = "card_key", nullable = false, length = 64, unique = true)
    private String cardKey;

    @Column(name = "large_key", nullable = false, length = 64, unique = true)
    private String largeKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductPhoto() {
    }

    public static ProductPhoto of(long productId, int position, String cardKey, String largeKey, int width,
                                  int height, Instant now) {
        var photo = new ProductPhoto();
        photo.productId = productId;
        photo.position = position;
        photo.cardKey = cardKey;
        photo.largeKey = largeKey;
        photo.width = width;
        photo.height = height;
        photo.createdAt = now;
        photo.updatedAt = now;
        return photo;
    }

    /** Sıralama ve silme sonrası sıkıştırma; tekil kısıt transaction sonunda denetlenir. */
    public void moveTo(int newPosition, Instant now) {
        if (position != newPosition) {
            position = newPosition;
            updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getPosition() {
        return position;
    }

    public String getCardKey() {
        return cardKey;
    }

    public String getLargeKey() {
        return largeKey;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
