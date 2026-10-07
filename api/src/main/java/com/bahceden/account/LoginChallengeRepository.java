package com.bahceden.account;

import com.bahceden.account.domain.LoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * İkinci faktör girişimleri (design.md D8).
 */
public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    /**
     * Aynı hesapta aynı kod hash'inin tekrar üretilip üretilmediğini denetler;
     * {@code code_hash} tekildir ve çakışma 500 yerine yeni kodla çözülür.
     */
    boolean existsByCodeHash(String codeHash);
}
