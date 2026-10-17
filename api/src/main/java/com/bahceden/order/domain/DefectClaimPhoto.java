package com.bahceden.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Talep fotoğrafı (ADR-081): ilan fotoğrafıyla aynı hattan geçmiş iki JPEG.
 * Yalnızca alıcıya, satıcıya ve admin'e sunulur; anahtar log'a yazılmaz (ADR-084).
 */
@Entity
@Table(name = "defect_claim_photos")
public class DefectClaimPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private DefectClaim claim;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "card_key", nullable = false, length = 64, updatable = false)
    private String cardKey;

    @Column(name = "large_key", nullable = false, length = 64, updatable = false)
    private String largeKey;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DefectClaimPhoto() {
    }

    static DefectClaimPhoto of(DefectClaim claim, int position, String cardKey, String largeKey, int width, int height,
                               Instant now) {
        var photo = new DefectClaimPhoto();
        photo.claim = claim;
        photo.position = position;
        photo.cardKey = cardKey;
        photo.largeKey = largeKey;
        photo.width = width;
        photo.height = height;
        photo.createdAt = now;
        photo.updatedAt = now;
        return photo;
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
