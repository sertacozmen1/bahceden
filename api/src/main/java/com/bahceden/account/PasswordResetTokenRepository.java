package com.bahceden.account;

import com.bahceden.account.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Parola sıfırlama token'ı sorguları (add-password-reset design.md D1).
 *
 * <p>Arama <strong>hash</strong> üzerinden yapılır. Satır kilitlenerek okunur:
 * aynı bağlantıyla gelen iki eşzamanlı istekten biri bekler ve kullanılmış
 * token'ı görür, parola iki kez değiştirilemez (AGENTS.md §11.4).
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Hesabın kullanılmamış token'larını kapatır: yalnızca en son bağlantı geçerli kalır (D1). */
    @Modifying
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.accountId = :accountId
               and t.usedAt is null""")
    int closeOpenTokens(@Param("accountId") Long accountId, @Param("now") Instant now);

    /**
     * Süresi verilen andan önce dolmuş sıfırlama token'larını siler (add-stale-record-cleanup D2).
     *
     * @return silinen satır sayısı
     */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
