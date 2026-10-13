package com.bahceden.order;

import com.bahceden.order.domain.DefectClaimChange;
import org.springframework.data.repository.Repository;

/**
 * Talep geçmişi: yalnızca ekleme (ADR-080). Çıplak {@link Repository}:
 * geçmişi değiştirmek için bir yol açılmaz.
 */
public interface DefectClaimChangeRepository extends Repository<DefectClaimChange, Long> {

    DefectClaimChange save(DefectClaimChange change);
}
