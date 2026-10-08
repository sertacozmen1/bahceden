package com.bahceden.catalog;

import com.bahceden.catalog.domain.ProductStatusChange;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * İlan durum geçmişi: yalnızca ekleme ve sıralı okuma (add-product-listings D1).
 * Çıplak {@link Repository}: silme ve toplu güncelleme yolu yoktur (ADR-040 deseni).
 */
public interface ProductStatusChangeRepository extends Repository<ProductStatusChange, Long> {

    ProductStatusChange save(ProductStatusChange change);

    List<ProductStatusChange> findByProductIdOrderByCreatedAtAscIdAsc(long productId);
}
