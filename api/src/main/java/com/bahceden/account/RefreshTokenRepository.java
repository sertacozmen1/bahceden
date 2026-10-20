package com.bahceden.account;

import com.bahceden.account.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Yenileme token'ı kayıtları (design.md D1).
 *
 * <p>Arama düz token ile değil <strong>hash</strong> ile yapılır. Yenileme ve
 * çıkış aynı satırı kilitleyerek okur ({@code SELECT … FOR UPDATE}): aynı
 * token'la gelen iki eşzamanlı istekten biri bekler ve kullanılmış token'ı
 * görür. Böylece rotasyon iki kez yapılamaz (AGENTS.md §11.4).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Ailenin henüz iptal edilmemiş tüm token'larını tek sorguda iptal eder (D1). */
    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.familyId = :familyId
               and t.revokedAt is null""")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /**
     * Hesabın bütün ailelerindeki iptal edilmemiş token'ları iptal eder: parola
     * sıfırlandığında tüm oturumlar kapanır (add-password-reset design.md D3).
     */
    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.accountId = :accountId
               and t.revokedAt is null""")
    int revokeAllForAccount(@Param("accountId") Long accountId, @Param("now") Instant now);

    /**
     * Bütün token'larının süresi verilen andan önce dolmuş aileleri siler
     * (add-stale-record-cleanup D2). Canlı bir ailenin eski token'ı silinmez:
     * tekrar sunulursa aile iptal edilmelidir (ADR-018).
     *
     * @return silinen satır sayısı
     */
    @Modifying
    @Query("""
            delete from RefreshToken t
             where t.familyId in (
                   select f.familyId from RefreshToken f
                    group by f.familyId
                   having max(f.expiresAt) < :cutoff)""")
    int deleteExpiredFamiliesBefore(@Param("cutoff") Instant cutoff);
}
