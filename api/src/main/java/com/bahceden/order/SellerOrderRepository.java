package com.bahceden.order;

import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Satıcı siparişleri.
 *
 * <p>Satıcının her sorgusu kendi satıcı kimliğiyle kurulur (ADR-007); kimlik
 * onaylı satıcı kapısından gelir (ADR-044). Liste ve detay sorguları ilişkileri
 * {@code join fetch} ile getirir; sorgu sayısı satır sayısından bağımsızdır.
 */
public interface SellerOrderRepository extends JpaRepository<SellerOrder, Long> {

    /** Alıcının sipariş detayı için: satıcılar ve satırlar tek sorguda. */
    @Query("""
            select distinct so from SellerOrder so
            join fetch so.seller
            join fetch so.items
            where so.order.id = :orderId
            order by so.id
            """)
    List<SellerOrder> findForOrder(long orderId);

    /** Satıcının görebileceği satıcı siparişleri (ödemesi tamamlanmış olanlar), en yeni üstte. */
    @Query(value = """
            select so from SellerOrder so
            join fetch so.order
            where so.seller.id = :sellerId and so.status in :statuses
            order by so.createdAt desc, so.id desc
            """,
            countQuery = "select count(so) from SellerOrder so where so.seller.id = :sellerId and so.status in :statuses")
    Page<SellerOrder> findForSeller(long sellerId, Collection<SellerOrderStatus> statuses, Pageable pageable);

    /**
     * İptal için alıcının satıcı siparişini kilitler (D9): aynı satıcı
     * siparişinin eşzamanlı iki iptali sırayla işlenir, ikincisi iptal edilmiş
     * kaydı görür.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select so from SellerOrder so where so.id = :id and so.order.id = :orderId")
    Optional<SellerOrder> findForBuyerCancel(long id, long orderId);

    /** İptal için satıcının kendi satıcı siparişini kilitler (D9). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select so from SellerOrder so where so.id = :id and so.seller.id = :sellerId")
    Optional<SellerOrder> findForSellerCancel(long id, long sellerId);

    /** Satıcının tek satıcı siparişi; başkasınınki veya ödenmemiş olan boş döner. */
    @Query("""
            select distinct so from SellerOrder so
            join fetch so.order
            join fetch so.items
            where so.id = :id and so.seller.id = :sellerId and so.status in :statuses
            """)
    Optional<SellerOrder> findOwnBySeller(long id, long sellerId, Collection<SellerOrderStatus> statuses);
}
