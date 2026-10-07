package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * İkinci faktör girişimi: e-posta ile gönderilen tek kullanımlık kod
 * (ADR-017, design.md D8).
 *
 * <p>Yalnızca hesabın ikinci faktör anahtarı açıksa oluşur. Kodun kendisi
 * saklanmaz; {@code SHA-256(hesapKimliği:kod)} saklanır. Hesap kimliği hash'e
 * girer, çünkü kod tek başına aranmaz: ikinci adım her zaman belirli bir
 * hesabın girişimine karşı doğrulanır (D8).
 */
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "code_hash", nullable = false, length = 64, unique = true)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginChallenge() {
    }

    public static LoginChallenge issue(Long accountId, String codeHash, Instant now, Instant expiresAt) {
        var challenge = new LoginChallenge();
        challenge.accountId = accountId;
        challenge.codeHash = codeHash;
        challenge.createdAt = now;
        challenge.expiresAt = expiresAt;
        challenge.attemptCount = 0;
        return challenge;
    }

    /** Bu girişim için izin verilen hatalı kod denemesi doldu mu. */
    public boolean attemptsExhausted(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    /**
     * Sunulan kodun hash'i saklananla eşleşiyor mu. Karşılaştırma sabit
     * sürelidir: eşleşen önek uzunluğu yanıt süresinden okunamaz.
     */
    public boolean matches(String candidateHash) {
        return MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII));
    }

    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public Long getId() {
        return id;
    }
}
