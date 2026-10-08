package com.bahceden.order;

import com.bahceden.order.domain.OrderStatusChange;
import org.springframework.data.repository.Repository;

/**
 * Satıcı siparişi durum geçmişi: yalnızca ekleme (add-cart-and-checkout D6).
 * Çıplak {@link Repository}: geçmişi değiştirmek için bir yol açılmaz.
 */
public interface OrderStatusChangeRepository extends Repository<OrderStatusChange, Long> {

    OrderStatusChange save(OrderStatusChange change);
}
