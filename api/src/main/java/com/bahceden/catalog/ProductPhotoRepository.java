package com.bahceden.catalog;

import com.bahceden.catalog.domain.ProductPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * İlan fotoğrafları (add-product-listings D3, D5, D6). Liste uçları kapakları
 * ve fotoğrafları sayfadaki ürün kimlikleriyle <strong>tek</strong> sorguda
 * alır; sorgu sayısı ürün sayısından bağımsızdır.
 */
public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, Long> {

    List<ProductPhoto> findByProductIdOrderByPositionAsc(long productId);

    int countByProductId(long productId);

    Optional<ProductPhoto> findByIdAndProductId(long id, long productId);

    /** Vitrin ve admin listesi: sayfadaki ürünlerin kapakları (D6). */
    @Query("select p from ProductPhoto p where p.productId in :productIds and p.position = 0")
    List<ProductPhoto> findCovers(Collection<Long> productIds);

    /** Satıcı paneli listesi: sayfadaki ürünlerin bütün fotoğrafları, sıralı. */
    @Query("select p from ProductPhoto p where p.productId in :productIds order by p.productId, p.position")
    List<ProductPhoto> findAllOf(Collection<Long> productIds);

    /**
     * Fotoğraf ucu yalnızca bu tabloda kayıtlı anahtarı sunar (D5): satıcı
     * belgeleri aynı dizinde, aynı anahtar biçimiyle durur.
     */
    @Query("select count(p) > 0 from ProductPhoto p where p.cardKey = :key or p.largeKey = :key")
    boolean isPhotoKey(String key);
}
