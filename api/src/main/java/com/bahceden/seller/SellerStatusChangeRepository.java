package com.bahceden.seller;

import com.bahceden.seller.domain.SellerStatusChange;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Durum geçmişi: yalnızca ekleme ve sıralı okuma (ADR-037 §7, design.md D1).
 *
 * <p>{@code JpaRepository} yerine çıplak {@link Repository} bilinçli seçildi:
 * {@code delete} ve toplu güncelleme metotları hiç yoktur, geçmişi değiştirmek
 * için bir yol açılmaz.
 */
public interface SellerStatusChangeRepository extends Repository<SellerStatusChange, Long> {

    SellerStatusChange save(SellerStatusChange change);

    List<SellerStatusChange> findBySellerIdOrderByCreatedAtAscIdAsc(long sellerId);
}
