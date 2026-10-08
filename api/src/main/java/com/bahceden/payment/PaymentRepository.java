package com.bahceden.payment;

import com.bahceden.payment.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ödemeler; siparişe kimliğiyle ulaşılır (sipariş başına tek ödeme). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(long orderId);
}
