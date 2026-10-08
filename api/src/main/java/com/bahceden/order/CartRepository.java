package com.bahceden.order;

import com.bahceden.order.domain.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sepetler; hesaba yalnızca token'daki kimlikle ulaşılır (ADR-007). */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByAccountId(long accountId);
}
