package com.bahceden.account;

import com.bahceden.account.domain.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * E-posta doğrulama token'ı sorguları.
 *
 * <p>Arama <strong>hash</strong> üzerinden yapılır: düz token veritabanında
 * yok (design.md D3). Hash eşitliği indeksli tek bir sorgudur.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
