package com.bahceden.account;

import com.bahceden.account.domain.LoginChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * İkinci faktör girişimleri (design.md D8).
 */
public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    /**
     * Aynı hesapta aynı kod hash'inin tekrar üretilip üretilmediğini denetler;
     * {@code code_hash} tekildir ve çakışma 500 yerine yeni kodla çözülür.
     */
    boolean existsByCodeHash(String codeHash);

    /**
     * Hesabın en son açılan, kullanılmamış ve süresi geçmemiş girişimi. Yalnızca
     * son kod geçerlidir (D8). Satır kilitlenir: eşzamanlı denemeler deneme
     * sayacını kaybetmez ve doğru kod iki oturum açamaz.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginChallenge> findFirstByAccountIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
            Long accountId, Instant now);

    /**
     * Hesabın kullanılmamış girişimlerini kapatır. Açık kodlar eski parolayla
     * elde edilmiştir; parola sıfırlandıktan sonra geçerli kalmamalıdır
     * (add-password-reset design.md D3).
     */
    @Modifying
    @Query("""
            update LoginChallenge c
               set c.usedAt = :now
             where c.accountId = :accountId
               and c.usedAt is null""")
    int closeOpenChallenges(@Param("accountId") Long accountId, @Param("now") Instant now);
}
