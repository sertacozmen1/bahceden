package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Parola sıfırlama token'ı (add-password-reset design.md D1, ADR-033).
 *
 * <p>Düz token <strong>saklanmaz</strong>: yalnızca SHA-256 hash'i tutulur ve
 * düz değer kullanıcıya bir kez, e-posta içinde verilir.
 *
 * <p>{@link EmailVerificationToken} ile aynı şekildedir ama ayrı tablodadır:
 * bu token hesabı devralma yetkisi verir, bu yüzden ömrü kısadır (1 saat) ve
 * bir doğrulama token'ının onun yerine kabul edilmesi şema düzeyinde imkânsızdır.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

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

    protected PasswordResetToken() {
    }

    public static PasswordResetToken issue(Long accountId, String tokenHash, Instant now, Instant expiresAt) {
        var token = new PasswordResetToken();
        token.accountId = accountId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Token geçerli mi: süresi dolmamış, kullanılmamış ve yeni bir talep tarafından kapatılmamış. */
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
}
