package com.bahceden.payment;

import com.bahceden.payment.domain.SellerPayoutAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Satıcı ödeme hesapları. Satıcıya yalnızca kendi satıcı kimliğiyle
 * ulaşılır; kimlik token'dan bulunan onaylı satıcı kaydından gelir (ADR-007).
 */
public interface SellerPayoutAccountRepository extends JpaRepository<SellerPayoutAccount, Long> {

    Optional<SellerPayoutAccount> findBySellerId(long sellerId);

    /** Ödeme isteğinde satıcı payları için: verilen satıcıların etkin hesapları, tek sorguda. */
    @Query("""
            select a from SellerPayoutAccount a
            where a.sellerId in :sellerIds
              and a.status = com.bahceden.payment.domain.PayoutAccountStatus.ACTIVE
            """)
    List<SellerPayoutAccount> findActiveBySellerIds(Collection<Long> sellerIds);
}
