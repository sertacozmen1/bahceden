package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Opak yenileme token'ı (ADR-008, design.md D1).
 *
 * <p>Düz token <strong>saklanmaz</strong>: yalnızca SHA-256 hash'i tutulur;
 * veritabanını okuyan biri oturum devralamaz. Her oturum bir <em>aile</em>
 * ({@code familyId}) başlatır; rotasyonla üretilen token'lar aynı ailede kalır,
 * böylece sızıntı tespitinde ailenin tamamı tek sorguda iptal edilebilir.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    /**
     * @param familyId  oturum zinciri; girişte yeni, rotasyonda eskisinin ailesi
     * @param tokenHash düz token'ın SHA-256 hash'i
     */
    public static RefreshToken issue(Long accountId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        var token = new RefreshToken();
        token.accountId = accountId;
        token.familyId = familyId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Kullanılmış token'ın tolerans penceresi dışında tekrar sunulması sızıntı işaretidir (D1, ADR-069). */
    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Token {@code grace} süresinden daha kısa bir süre önce mi kullanıldı?
     * Tolerans penceresindeki tekrar kullanım sızıntı sayılmaz (ADR-069).
     */
    public boolean usedWithin(Duration grace, Instant now) {
        return usedAt != null && now.isBefore(usedAt.plus(grace));
    }

    /** Rotasyon: bu token bir daha kabul edilmez, yerine aynı ailede yenisi üretilir. */
    public void markUsed(Instant now) {
        usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public UUID getFamilyId() {
        return familyId;
    }
}
