package com.bahceden.order;

import com.bahceden.order.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Teslimat adresleri. Sahiplik sorgu seviyesindedir (ADR-007): başkasının
 * adresi boş döner ve uç 404 verir.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByIdAndAccountId(long id, long accountId);

    List<Address> findByAccountIdOrderByIdAsc(long accountId);

    int countByAccountId(long accountId);
}
