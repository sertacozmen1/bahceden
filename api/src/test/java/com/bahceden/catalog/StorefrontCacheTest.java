package com.bahceden.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.cache.StorefrontCache;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Vitrin okuma cache'i (storefront-catalog "Vitrin okumaları kısa süre
 * cache'lenir ve değişiklikte tazelenir", ADR-109).
 *
 * <p>{@code critical}: eski cache, satılamayan ürünü satılabilir ya da
 * kaldırılmış ürünü görünür gösterirdi. Testler gerçek Redis'e karşı koşar.
 * Değişiklikler servis yolundan (HTTP uçları) yapılır; veri kurulumu ise
 * fixture'larla JDBC üzerinden yapılır ve cache'i bilerek atlar.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Vitrin cache'i: okuma, silme ve görünürlük")
class StorefrontCacheTest {

    private static final String SLUG = "cache-domates";
    private static final String DETAIL = "/api/v1/products/" + SLUG;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CacheManager caches;

    @Autowired
    private StorefrontCache storefront;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ListingFixtures fixtures;
    private long sellerAccount;
    private long productId;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        sellerAccount = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(sellerAccount);
        productId = fixtures.publishedProduct(sellerAccount, SLUG, 2450, 10);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Ürün detayı ikinci okumada veritabanına gitmez")
    void detay_ikinci_okumada_cacheten_gelir() throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(DETAIL)).andExpect(status().isOk());
        long afterFirst = statistics.getPrepareStatementCount();

        mockMvc.perform(get(DETAIL)).andExpect(status().isOk()).andExpect(jsonPath("$.slug").value(SLUG));

        assertThat(afterFirst).isPositive();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("Vitrin listesinin ilk sayfası cache'lenir; derin sayfa cache'lenmez")
    void liste_ilk_sayfalar_cachelenir() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "0").param("size", "20")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products").param("page", "5").param("size", "20")).andExpect(status().isOk());

        var list = caches.getCache(StorefrontCache.LIST);
        assertThat(list).isNotNull();
        assertThat(list.get("0:20")).isNotNull();
        assertThat(list.get("5:20")).isNull();
    }

    @Test
    @DisplayName("Satıcı fiyatı güncelleyince sonraki detay ve liste okuması yeni fiyatı gösterir (spec: değişiklik sonraki okumada görünür)")
    void guncelleme_sonraki_okumada_gorunur() throws Exception {
        readDetailAndList(2450);

        mockMvc.perform(put("/api/v1/sellers/me/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(sellerAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ürün " + SLUG, "unit", "KILOGRAM", "priceAmount", 3100, "stockQuantity", 10))))
                .andExpect(status().isOk());

        readDetailAndList(3100);
    }

    @Test
    @DisplayName("Admin ürünü kaldırınca detay 404 döner ve ürün listede görünmez (spec: kaldırılan ürün cache'ten dönmez)")
    void kaldirilan_urun_cacheten_donmez() throws Exception {
        readDetailAndList(2450);
        var admin = fixtures.token(fixtures.account("admin@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_ADMIN"),
                "ROLE_CUSTOMER", "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/admin/products/" + productId + "/remove")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Ürün açıklaması yanıltıcı."))))
                .andExpect(status().isOk());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + SLUG + "')]").isEmpty());
    }

    @Test
    @DisplayName("Satıcı askıya alınınca ürünü detayda 404 ve listede yok (spec: satıcısı askıya alınır)")
    void satici_askiya_alininca_urun_dusur() throws Exception {
        readDetailAndList(2450);
        var admin = fixtures.token(fixtures.account("admin@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_ADMIN"),
                "ROLE_CUSTOMER", "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/admin/sellers/" + fixtures.sellerIdOf(sellerAccount) + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Belge süresi doldu, yenileyin."))))
                .andExpect(status().isOk());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + SLUG + "')]").isEmpty());
    }

    @Test
    @DisplayName("Ödeme stok düşürünce sonraki detay okuması yeni stoğu gösterir")
    void odeme_stok_dusurunce_tazelenir() throws Exception {
        mockMvc.perform(get(DETAIL)).andExpect(jsonPath("$.stockQuantity").value(10));
        var buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        fixtures.cartItem(buyer, productId, 3);

        new OrderFlows(mockMvc, jdbc, objectMapper, fixtures).paidOrder(buyer, fixtures.token(buyer, "ROLE_CUSTOMER"));

        mockMvc.perform(get(DETAIL)).andExpect(jsonPath("$.stockQuantity").value(7));
    }

    @Test
    @DisplayName("Geri alınan transaction cache'i silmez; commit edilen siler (spec: geri alınan değişiklik)")
    void geri_alinan_transaction_cachei_silmez() throws Exception {
        mockMvc.perform(get(DETAIL)).andExpect(status().isOk());
        var product = caches.getCache(StorefrontCache.PRODUCT);
        assertThat(product).isNotNull();
        var transactions = new TransactionTemplate(transactionManager);

        transactions.executeWithoutResult(status -> {
            storefront.evictProducts(List.of(SLUG));
            status.setRollbackOnly();
        });
        assertThat(product.get(SLUG)).isNotNull();

        transactions.executeWithoutResult(status -> storefront.evictProducts(List.of(SLUG)));
        assertThat(product.get(SLUG)).isNull();
    }

    @Test
    @DisplayName("Görünmeyen ürünün 404'ü cache'lenmez: ürün yayına çıkınca hemen görünür")
    void bulunamadi_cachelenmez() throws Exception {
        jdbc.update("update products set status = 'DRAFT' where id = ?", productId);
        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());

        jdbc.update("update products set status = 'PUBLISHED' where id = ?", productId);

        mockMvc.perform(get(DETAIL)).andExpect(status().isOk());
    }

    private void readDetailAndList(long expectedPrice) throws Exception {
        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice.amount").value(expectedPrice));
        mockMvc.perform(get("/api/v1/products").param("size", "20"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + SLUG + "')].unitPrice.amount").value((int) expectedPrice));
    }
}
