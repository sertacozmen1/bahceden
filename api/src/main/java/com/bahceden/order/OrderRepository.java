package com.bahceden.order;

import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Siparişler. Alıcının her sorgusu kendi hesap kimliğiyle kurulur (ADR-007):
 * başkasının siparişi boş döner ve uç 404 verir.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Aynı {@code Idempotency-Key} ile tekrarlanan istek (D6). */
    Optional<Order> findByBuyerAccountIdAndIdempotencyKey(long buyerAccountId, String idempotencyKey);

    Optional<Order> findByOrderNumberAndBuyerAccountId(String orderNumber, long buyerAccountId);

    /**
     * Ödeme tamamlama için siparişi kilitler (D8): aynı siparişin eşzamanlı iki
     * tamamlama çağrısı sırayla işlenir, ikincisi sonuçlanmış siparişi görür.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber and o.buyerAccountId = :buyerAccountId")
    Optional<Order> findForPayment(String orderNumber, long buyerAccountId);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Ödeme süresi verilen andan önce dolmuş ve hâlâ ödeme bekleyen siparişler,
     * en eski süreliden (add-stale-record-cleanup D1). İş ilk sayfayı bununla,
     * sonrakileri {@link #findExpiredBeforeAfter} ile okur (ADR-100). Yalnızca
     * imleç döner; her sipariş işlenirken ayrıca kilitlenir.
     */
    @Query("""
            select new com.bahceden.order.SweepCursor(o.id, o.paymentExpiresAt) from Order o
            where o.status = :status and o.paymentExpiresAt < :cutoff
            order by o.paymentExpiresAt, o.id""")
    List<SweepCursor> findExpiredBefore(OrderStatus status, Instant cutoff, Pageable pageable);

    /** Aynı sorgu, imleçten ({@code afterDue}, {@code afterId}) sonrası (ADR-100); aynı kısmi indeks. */
    @Query("""
            select new com.bahceden.order.SweepCursor(o.id, o.paymentExpiresAt) from Order o
            where o.status = :status and o.paymentExpiresAt < :cutoff
              and (o.paymentExpiresAt > :afterDue or (o.paymentExpiresAt = :afterDue and o.id > :afterId))
            order by o.paymentExpiresAt, o.id""")
    List<SweepCursor> findExpiredBeforeAfter(OrderStatus status, Instant cutoff, Instant afterDue, long afterId,
                                             Pageable pageable);

    /** Temizlik işi için siparişi kilitler; ödeme dönüşüyle aynı anda çalışırsa sırayla işlenir (D1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(long id);

    /** Alıcının siparişleri: en yeni üstte. */
    @Query("select o from Order o where o.buyerAccountId = :buyerAccountId order by o.createdAt desc, o.id desc")
    Page<Order> findOwnNewestFirst(long buyerAccountId, Pageable pageable);
}
