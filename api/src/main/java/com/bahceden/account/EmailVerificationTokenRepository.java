package com.bahceden.account;

import com.bahceden.account.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * E-posta doğrulama token'ı sorguları.
 *
 * <p>Arama <strong>hash</strong> üzerinden yapılır: düz token veritabanında
 * yok (design.md D3). Hash eşitliği indeksli tek bir sorgudur.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Süresi verilen andan önce dolmuş doğrulama token'larını siler (add-stale-record-cleanup D2).
     *
     * @return silinen satır sayısı
     */
    @Modifying
    @Query("delete from EmailVerificationToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
