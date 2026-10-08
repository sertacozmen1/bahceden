package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
