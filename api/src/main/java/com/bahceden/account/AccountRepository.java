package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.AccountRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Rolü taşıyan bütün hesaplar, kimlik sırasıyla. Admin bildirimleri için
     * (ADR-101); admin hesapları az sayıdadır ve veritabanından verilir (ADR-046).
     */
    @Query("select a from Account a where :role member of a.roles order by a.id")
    List<Account> findByRole(AccountRole role);
}
