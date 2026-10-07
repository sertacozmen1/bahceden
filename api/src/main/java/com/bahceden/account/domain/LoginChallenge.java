package com.bahceden.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    public Long getId() {
        return id;
    }
}
