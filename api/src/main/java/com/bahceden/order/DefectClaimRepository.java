package com.bahceden.order;

import com.bahceden.order.domain.DefectClaim;
import com.bahceden.order.domain.DefectClaimStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Ayıplı ürün talepleri (ADR-079). Sahiplik satıcı siparişinin sorgusunda
 * denetlenir (ADR-007); buradaki okuma sorguları yalnızca zaten
 * yetkilendirilmiş satıcı siparişi kimlikleriyle çağrılır. Satırlar, sipariş
 * satırları ve fotoğraflar tek sorguda gelir.
 */
public interface DefectClaimRepository extends JpaRepository<DefectClaim, Long> {

    /**
     * Satıcının yanıtı için talebi kilitler: sahiplik sorguda (satıcı kimliği,
     * ADR-007). Aynı talebin eşzamanlı yanıtı ve süre işi sırayla işlenir.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DefectClaim c where c.sellerOrder.id = :sellerOrderId and c.sellerOrder.seller.id = :sellerId")
    Optional<DefectClaim> findForSellerUpdate(long sellerOrderId, long sellerId);

    /** Alıcının yanıtı için talebi kilitler: sahiplik sorguda (sipariş numarası ve alıcı kimliği). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from DefectClaim c
            where c.sellerOrder.id = :sellerOrderId
              and c.sellerOrder.order.orderNumber = :orderNumber
              and c.sellerOrder.order.buyerAccountId = :accountId
            """)
    Optional<DefectClaim> findForBuyerUpdate(long sellerOrderId, String orderNumber, long accountId);

    /**
     * Süresi dolmuş taleplerin kimlikleri, en eski süre önce (ADR-083): satıcı
     * süresi geçmiş açık talepler ve alıcı süresi geçmiş teklif veya ret.
     * Kısmi indeksler ({@code 014-defect-claims.sql}) bu iki koşula göredir.
     */
    @Query("""
            select c.id from DefectClaim c
            where (c.status = com.bahceden.order.domain.DefectClaimStatus.OPEN and c.sellerDeadlineAt <= :now)
               or (c.status in (com.bahceden.order.domain.DefectClaimStatus.OFFERED,
                                com.bahceden.order.domain.DefectClaimStatus.REJECTED)
                   and c.buyerDeadlineAt <= :now)
            order by coalesce(c.buyerDeadlineAt, c.sellerDeadlineAt), c.id
            """)
    List<Long> findOverdueIds(Instant now, Pageable pageable);

    /** Süre işi için talebi kilitler; kullanıcı eylemiyle eşzamanlılık sıraya girer (ADR-083). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DefectClaim c where c.id = :id")
    Optional<DefectClaim> findByIdForUpdate(long id);

    /** Admin listesi: durumla süzülür, en yeni üstte; satıcı siparişi, sipariş ve satıcı tek sorguda (ADR-086). */
    @Query(value = """
            select c from DefectClaim c
            join fetch c.sellerOrder so
            join fetch so.order
            join fetch so.seller
            where c.status = :status
            order by c.createdAt desc, c.id desc
            """,
            countQuery = "select count(c) from DefectClaim c where c.status = :status")
    Page<DefectClaim> findForAdmin(DefectClaimStatus status, Pageable pageable);

    /** Admin detayı: satırlar, sipariş satırları ve fotoğraflar tek sorguda. */
    @Query("""
            select distinct c from DefectClaim c
            join fetch c.sellerOrder so
            join fetch so.order
            join fetch so.seller
            left join fetch c.lines l
            left join fetch l.orderItem
            left join fetch c.photos
            where c.id = :id
            """)
    Optional<DefectClaim> findWithDetailsById(long id);

    @Query("select count(c) > 0 from DefectClaim c where c.sellerOrder.id = :sellerOrderId")
    boolean existsForSellerOrder(long sellerOrderId);

    @Query("""
            select distinct c from DefectClaim c
            left join fetch c.lines l
            left join fetch l.orderItem
            left join fetch c.photos
            where c.sellerOrder.id in :sellerOrderIds
            """)
    List<DefectClaim> findWithDetailsForSellerOrders(Collection<Long> sellerOrderIds);
}
