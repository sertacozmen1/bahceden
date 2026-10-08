package com.bahceden.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahceden.support.TestcontainersConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vitrin sorgusunun davranışı (storefront-catalog spec'i, design.md D2).
 *
 * <p>Gerçek PostgreSQL'e karşı koşar (ADR-005): kısmi indeks, {@code timestamptz}
 * davranışı ve sıralama motorun kendisine bağlıdır.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("ProductRepository: vitrin sorgusu")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("delete from product_status_changes");
        jdbc.execute("delete from product_photos");
        jdbc.execute("delete from products");
        jdbc.execute("delete from seller_status_changes");
        jdbc.execute("delete from seller_documents");
        jdbc.execute("delete from sellers");
    }

    @Test
    @DisplayName("Yalnızca PUBLISHED ürünler döner; DRAFT ve ARCHIVED gizlenir")
    void yalnizca_yayinlanmis_urunler_doner() {
        long seller = insertSeller("uretici-a", "Üretici A", "Manisa");
        insertProduct(seller, "yayinda", "Yayında", "PUBLISHED", Instant.now());
        insertProduct(seller, "taslak", "Taslak", "DRAFT", null);
        insertProduct(seller, "arsiv", "Arşiv", "ARCHIVED", Instant.now());

        var page = repository.findPublished(PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement()
                .satisfies(p -> assertThat(p.getSlug()).isEqualTo("yayinda"));
    }

    @Test
    @Tag("critical")
    @DisplayName("Onaylı olmayan satıcının PUBLISHED ürünü listede ve toplamda yok (ADR-038 §4)")
    void onayli_olmayan_saticinin_urunu_gizlenir() {
        long approved = insertSeller("onayli", "Onaylı", "Manisa");
        long pending = insertSeller("bekleyen", "Bekleyen", "Manisa", "PENDING");
        long rejected = insertSeller("reddedilen", "Reddedilen", "Manisa", "REJECTED");
        long suspended = insertSeller("askida", "Askıda", "Manisa", "SUSPENDED");
        insertProduct(approved, "gorunur", "Görünür", "PUBLISHED", Instant.now());
        insertProduct(pending, "bekleyenin", "Bekleyenin", "PUBLISHED", Instant.now());
        insertProduct(rejected, "reddedilenin", "Reddedilenin", "PUBLISHED", Instant.now());
        insertProduct(suspended, "askidakinin", "Askıdakinin", "PUBLISHED", Instant.now());

        var page = repository.findPublished(PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement()
                .satisfies(p -> assertThat(p.getSlug()).isEqualTo("gorunur"));
    }

    @Test
    @Tag("critical")
    @DisplayName("Askıdan çıkan satıcının ürünleri yeniden görünür")
    void askidan_cikan_saticinin_urunleri_geri_gelir() {
        long seller = insertSeller("donen", "Dönen", "Manisa", "SUSPENDED");
        insertProduct(seller, "donen-urun", "Dönen Ürün", "PUBLISHED", Instant.now());
        assertThat(repository.findPublished(PageRequest.of(0, 20)).getTotalElements()).isZero();

        jdbc.update("update sellers set status = 'APPROVED' where id = ?", seller);

        assertThat(repository.findPublished(PageRequest.of(0, 20)).getContent()).singleElement()
                .satisfies(p -> assertThat(p.getSlug()).isEqualTo("donen-urun"));
    }

    @Test
    @DisplayName("Sıralama yayın tarihine göre yeniden eskiye")
    void siralama_yeniden_eskiye() {
        long seller = insertSeller("uretici-b", "Üretici B", "Antalya");
        var now = Instant.now();
        insertProduct(seller, "eski", "Eski", "PUBLISHED", now.minus(5, ChronoUnit.DAYS));
        insertProduct(seller, "yeni", "Yeni", "PUBLISHED", now.minus(1, ChronoUnit.DAYS));
        insertProduct(seller, "orta", "Orta", "PUBLISHED", now.minus(3, ChronoUnit.DAYS));

        var slugs = repository.findPublished(PageRequest.of(0, 20)).getContent()
                .stream().map(p -> p.getSlug()).toList();

        assertThat(slugs).containsExactly("yeni", "orta", "eski");
    }

    @Test
    @DisplayName("Eşit yayın tarihinde sıralama kararlı: aynı kayıt iki sayfada görünmez")
    void esit_tarihte_siralama_kararli() {
        long seller = insertSeller("uretici-c", "Üretici C", "İzmir");
        var sameMoment = Instant.now();
        for (int i = 1; i <= 4; i++) {
            insertProduct(seller, "urun-" + i, "Ürün " + i, "PUBLISHED", sameMoment);
        }

        var first = repository.findPublished(PageRequest.of(0, 2)).getContent()
                .stream().map(p -> p.getId()).toList();
        var second = repository.findPublished(PageRequest.of(1, 2)).getContent()
                .stream().map(p -> p.getId()).toList();

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    @Test
    @DisplayName("Boş katalogda hata değil boş sayfa döner")
    void bos_katalog_bos_sayfa_doner() {
        var page = repository.findPublished(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Aralık dışı sayfa boş içerik ve doğru toplam döner")
    void aralik_disi_sayfa() {
        long seller = insertSeller("uretici-d", "Üretici D", "Mersin");
        insertProduct(seller, "tek-urun", "Tek Ürün", "PUBLISHED", Instant.now());

        var page = repository.findPublished(PageRequest.of(5, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Üretici tek sorguda gelir: ürün sayısı sorgu sayısını artırmaz (N+1 yok)")
    void uretici_tek_sorguda_gelir() {
        long seller = insertSeller("uretici-e", "Üretici E", "Manisa");
        for (int i = 1; i <= 10; i++) {
            insertProduct(seller, "coklu-" + i, "Çoklu " + i, "PUBLISHED", Instant.now().minus(i, ChronoUnit.HOURS));
        }

        var statistics = statistics();
        statistics.clear();

        var page = repository.findPublished(PageRequest.of(0, 10));
        // Üretici adına erişim tembel yükleme tetiklememeli: fetch join ile geldi.
        List<String> sellerNames = page.getContent().stream()
                .map(p -> p.getSeller().getDisplayName()).toList();

        assertThat(sellerNames).hasSize(10).containsOnly("Üretici E");
        // İçerik sorgusu + sayım sorgusu = 2. Ürün sayısından bağımsız sabit.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long insertSeller(String slug, String displayName, String city) {
        return insertSeller(slug, displayName, city, "APPROVED");
    }

    private long insertSeller(String slug, String displayName, String city, String status) {
        jdbc.update("insert into sellers (slug, display_name, city, status) values (?, ?, ?, ?)",
                slug, displayName, city, status);
        return jdbc.queryForObject("select id from sellers where slug = ?", Long.class, slug);
    }

    private void insertProduct(long sellerId, String slug, String name, String status, Instant publishedAt) {
        jdbc.update("""
                insert into products (seller_id, slug, name, status, unit, price_amount,
                                      price_currency, stock_quantity, published_at)
                values (?, ?, ?, ?, 'KILOGRAM', 2450, 'TRY', 10, ?)
                """, sellerId, slug, name, status, publishedAt == null ? null : java.sql.Timestamp.from(publishedAt));
    }
}
