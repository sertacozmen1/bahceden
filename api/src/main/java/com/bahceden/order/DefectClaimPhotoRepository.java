package com.bahceden.order;

import com.bahceden.order.domain.DefectClaimPhoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Talep fotoğraflarının sunumu (ADR-084). Sahiplik sorgu seviyesindedir: hesap
 * talebin alıcısı ya da satıcısı değilse sonuç boştur ve uç 404 döner.
 */
public interface DefectClaimPhotoRepository extends Repository<DefectClaimPhoto, Long> {

    /** Talebin alıcısı veya satıcı hesabı için fotoğraf. */
    @Query("""
            select p from DefectClaimPhoto p
            join p.claim c
            join c.sellerOrder so
            join so.order o
            join so.seller s
            where c.id = :claimId and p.position = :position
              and (o.buyerAccountId = :accountId or s.accountId = :accountId)
            """)
    Optional<DefectClaimPhoto> findVisibleTo(long claimId, int position, long accountId);

    /** Admin için fotoğraf; hakemlik bütün taleplere bakar (ADR-084). */
    @Query("select p from DefectClaimPhoto p where p.claim.id = :claimId and p.position = :position")
    Optional<DefectClaimPhoto> findForAdmin(long claimId, int position);
}
