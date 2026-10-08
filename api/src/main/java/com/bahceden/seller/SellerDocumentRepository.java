package com.bahceden.seller;

import com.bahceden.seller.domain.SellerDocument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Başvuru belgelerinin meta verisi (design.md D6). Kaydın güncel belgesi en son
 * eklenen satırdır; önceki belgeler denetim izi için kalır.
 */
public interface SellerDocumentRepository extends JpaRepository<SellerDocument, Long> {

    Optional<SellerDocument> findFirstBySellerIdOrderByCreatedAtDescIdDesc(long sellerId);
}
