package com.bahceden.order;

import com.bahceden.order.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Sepet satırları.
 *
 * <p>Sepet okuması ürünü ve satıcıyı {@code join fetch} ile <strong>tek
 * sorguda</strong> getirir (add-cart-and-checkout D4); ödeme hesabının durumu
 * ürün satırındaki alt sorgudan gelir. Bu iddia sorgu sayısı testiyle sabittir.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select i from CartItem i
            join fetch i.product p
            join fetch p.seller
            where i.cartId = :cartId
            order by i.id
            """)
    List<CartItem> findLinesOf(long cartId);

    Optional<CartItem> findByCartIdAndProductId(long cartId, long productId);

    int countByCartId(long cartId);

    /** Ödeme başarılı olunca satın alınan ürünler sepetten silinir (D8). */
    @Modifying
    @Query("delete from CartItem i where i.cartId = :cartId and i.product.id in :productIds")
    void deleteProducts(long cartId, Collection<Long> productIds);
}
