package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    public Long getId() {
        return id;
    }
}
