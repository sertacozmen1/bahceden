package com.bahceden.seller;

import com.bahceden.seller.domain.Seller;
import com.bahceden.seller.domain.SellerStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Satıcı sorguları.
 *
 * <p>Sahiplik sorgu seviyesindedir (ADR-007): hesap kendi kaydına yalnızca
 * {@link #findByAccountId} ile, kimliği token'dan gelen hesap üzerinden ulaşır;
 * istemciden satıcı kimliği alınmaz.
 *
 * <p>Admin listesinde sıralama sorgunun içinde sabittir (yeniden eskiye, eşitlikte
 * kimlik): sayfalar arası tutarlılık ikincil ölçütle garanti edilir.
 */
public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByAccountId(long accountId);

    /**
     * Satıcıya özgü her işlemin kapısı (design.md D5): rol token'dan en geç 15
     * dakikada düşer, durum ise bu sorguda anında denetlenir.
     */
    Optional<Seller> findByAccountIdAndStatus(long accountId, SellerStatus status);

    boolean existsBySlug(String slug);

    @Query("select s from Seller s where s.status = :status order by s.createdAt desc, s.id desc")
    Page<Seller> findByStatusNewestFirst(SellerStatus status, Pageable pageable);

    @Query("select s from Seller s order by s.createdAt desc, s.id desc")
    Page<Seller> findAllNewestFirst(Pageable pageable);
}
