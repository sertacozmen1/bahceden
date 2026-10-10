package com.bahceden.shipping;

import com.bahceden.shipping.domain.Shipment;
import com.bahceden.shipping.domain.ShipmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Gönderiler (add-shipments D2).
 *
 * <p>"Etkin" gönderi iptal edilmemiş olandır; bir satıcı siparişinin en çok bir
 * etkin gönderisi vardır (kısmi benzersiz indeks). Satıcının etiketi gibi
 * sahiplik isteyen okumalar satıcı kimliğiyle sorguda kurulur (ADR-007).
 */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    @Query("select s from Shipment s where s.sellerOrderId = :sellerOrderId and s.status <> 'CANCELLED'")
    Optional<Shipment> findActive(long sellerOrderId);

    /** İptal için kilitli okuma (D6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.sellerOrderId = :sellerOrderId and s.status <> 'CANCELLED'")
    Optional<Shipment> findActiveForUpdate(long sellerOrderId);

    /** Satıcının kendi satıcı siparişinin etkin gönderisi; başkasınınki boş döner. */
    @Query("""
            select s from Shipment s, SellerOrder so
            where so.id = s.sellerOrderId and s.sellerOrderId = :sellerOrderId and so.seller.id = :sellerId
              and s.status <> 'CANCELLED'
            """)
    Optional<Shipment> findActiveForSeller(long sellerOrderId, long sellerId);

    @Query("select s from Shipment s where s.sellerOrderId in :sellerOrderIds and s.status <> 'CANCELLED'")
    List<Shipment> findActiveFor(Collection<Long> sellerOrderIds);

    /** Bu satıcı siparişi için şimdiye kadar açılmış gönderi sayısı (referans sırası, D3). */
    long countBySellerOrderId(long sellerOrderId);

    /** Durum sorgusunun sırası: en uzun süredir sorgulanmayan açık gönderiler (D5). */
    @Query("""
            select s.id from Shipment s where s.status in :statuses
            order by s.lastPolledAt asc nulls first, s.id asc
            """)
    List<Long> findIdsToPoll(Collection<ShipmentStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(long id);
}
