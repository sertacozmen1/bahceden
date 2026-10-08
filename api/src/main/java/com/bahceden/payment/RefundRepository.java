package com.bahceden.payment;

import com.bahceden.payment.domain.Refund;
import org.springframework.data.repository.Repository;

/**
 * İadeler: yalnızca eklenir (add-cart-and-checkout D8, D9). Çıplak
 * {@link Repository}: silme ve güncelleme yolu açılmaz.
 */
public interface RefundRepository extends Repository<Refund, Long> {

    Refund save(Refund refund);
}
