package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * E-posta doğrulama token'ı (design.md D3).
 *
 * <p>Düz token <strong>saklanmaz</strong>: yalnızca SHA-256 hash'i tutulur ve
 * düz değer kullanıcıya bir kez, e-posta içinde verilir. Veritabanını okuyan
 * biri doğrulama yapamaz.
 *
 * <p>Token tek kullanımlıktır ({@code usedAt}) ve süre sınırlıdır
 * ({@code expiresAt}). Doğrulanmış olmak girişi etkilemez (ADR-017); bu token
 * yalnızca {@code email_verified} bilgisini işaretler.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerificationToken() {
    }

    public static EmailVerificationToken issue(Long accountId, String tokenHash, Instant now, Instant expiresAt) {
        var token = new EmailVerificationToken();
        token.accountId = accountId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Token geçerli mi: süresi dolmamış ve daha önce kullanılmamış. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
