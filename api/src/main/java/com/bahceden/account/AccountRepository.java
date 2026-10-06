package com.bahceden.account;

import com.bahceden.account.domain.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Hesap sorguları.
 *
 * <p>E-posta ile arama normalleştirilmiş (küçük harf) değer üzerinden yapılır:
 * normalleştirme {@code AccountService} içinde, tek yerde olur. Böylece
 * {@code lower(email)} gibi bir fonksiyonel indekse ihtiyaç kalmaz ve tekil
 * kısıt gerçekten tekilliği garanti eder.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
