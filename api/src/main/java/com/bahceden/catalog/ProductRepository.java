package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Ürün sorguları.
 *
 * <p>Vitrin sorgusu üreticiyi {@code join fetch} ile <strong>tek sorguda</strong>
 * getirir (design.md D2): tembel ilişki listede N+1 üretirdi ve vitrin en çok
 * çağrılan uçtur. Bu iddia sorgu sayısı testiyle sabitlenmiştir; ilişki ayarı
 * sessizce değişirse test kırmızıya düşer.
 *
 * <p>Yalnızca satıcısı {@code APPROVED} olan ürünler görünür (ADR-038 §4,
 * add-seller-onboarding design.md D2). Kural ürünün durumuna değil sorguya
 * yazılıdır: satıcı askıdan çıktığında ürünleri, eski durumlarını hatırlamaya
 * gerek kalmadan kendiliğinden geri gelir.
 *
 * <p>Sıralama sorgunun içinde sabittir ({@code published_at desc, id desc}):
 * istemci bu dilimde sıralama parametresi göndermez ve eşit yayın tarihlerinde
 * sayfalar arası tutarlılık ikincil ölçütle garanti edilir.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            select p from Product p
            join fetch p.seller s
            where p.status = com.bahceden.catalog.domain.ProductStatus.PUBLISHED
              and s.status = com.bahceden.seller.domain.SellerStatus.APPROVED
            order by p.publishedAt desc, p.id desc
            """,
            // Sayım sorgusu açıkça yazılır: türetilmiş sayım, fetch join'i
            // taşıyamaz ve gereksiz birleştirme yapar.
            countQuery = """
            select count(p) from Product p
            join p.seller s
            where p.status = com.bahceden.catalog.domain.ProductStatus.PUBLISHED
              and s.status = com.bahceden.seller.domain.SellerStatus.APPROVED
            """)
    Page<Product> findPublished(Pageable pageable);

    /**
     * Satıcının kendi ilanı: sahiplik sorgu seviyesinde (ADR-007). Başka
     * satıcının ilanı boş döner ve uç 404 verir; varlık açığa çıkmaz.
     */
    Optional<Product> findByIdAndSellerId(long id, long sellerId);

    /** Satıcı paneli listesi: en son güncellenen üstte. Satıcı zaten bilinir, birleştirme yok. */
    @Query("select p from Product p where p.seller.id = :sellerId order by p.updatedAt desc, p.id desc")
    Page<Product> findOwnNewestFirst(long sellerId, Pageable pageable);

    boolean existsBySlug(String slug);

    /**
     * Sepete ekleme için ürün ve satıcısı tek sorguda (add-cart-and-checkout
     * D4); satın alınabilirlik {@link Product#isPurchasable()} ile, satıcı
     * yüklüyken karara bağlanır.
     */
    @Query("select p from Product p join fetch p.seller where p.id = :id")
    Optional<Product> findWithSellerById(long id);

    /**
     * Ödeme anında stok düşümü için ürünleri kilitler (add-cart-and-checkout D8).
     * Satırlar kimlik sırasıyla kilitlenir: aynı ürünleri içeren iki ödeme
     * birbirini kilitlenmeye (deadlock) sokmaz, ikincisi ilkinin bitmesini bekler.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllForUpdate(Collection<Long> ids);

    /**
     * Ürün detayı: vitrin listesiyle aynı görünürlük koşulu (D6, ADR-041).
     * Listede görünmeyen ürün doğrudan adresle de açılmaz.
     */
    @Query("""
            select p from Product p
            join fetch p.seller s
            where p.slug = :slug
              and p.status = com.bahceden.catalog.domain.ProductStatus.PUBLISHED
              and s.status = com.bahceden.seller.domain.SellerStatus.APPROVED
            """)
    Optional<Product> findVisibleBySlug(String slug);

    /**
     * Admin ilan listesi (add-product-listings D7): durumla süzülür, üretici
     * tek sorguda gelir; en son değişen üstte.
     */
    @Query(value = """
            select p from Product p
            join fetch p.seller
            where p.status = :status
            order by p.updatedAt desc, p.id desc
            """,
            countQuery = "select count(p) from Product p where p.status = :status")
    Page<Product> findForModeration(ProductStatus status, Pageable pageable);
}
