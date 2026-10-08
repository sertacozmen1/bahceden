package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Siparişlerin görüntülenmesi (orders spec'i "Alıcı kendi siparişlerini görür",
 * "Satıcı kendi satıcı siparişlerini görür"; add-cart-and-checkout D6).
 *
 * <p>{@code critical}: sahiplik (başkasının kaydı 404) ve alıcı adresinin
 * satıcıdan gizlenmesi (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Sipariş görüntüleme: /api/v1/orders ve /api/v1/sellers/me/orders")
class OrderViewTest {

    private static final AtomicInteger KEYS = new AtomicInteger();

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
    private ScriptedPaymentProvider provider;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;
    private long seller;
    private String sellerToken;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        sellerToken = fixtures.sellerToken(seller);
        fixtures.activePayoutAccount(seller);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
    }

    @Test
    @DisplayName("Alıcı yalnızca kendi siparişlerini listeler; başkasının detayı 404")
    void alici_sahipligi() throws Exception {
        var mine = paidOrder(buyer, buyerToken, seller, "benim");
        var other = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");
        var othersToken = fixtures.token(other, "ROLE_CUSTOMER");
        var theirs = paidOrder(other, othersToken, seller, "onun");

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(mine))
                .andExpect(jsonPath("$.items[0].status").value("PAID"));
        mockMvc.perform(get("/api/v1/orders/" + theirs).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_NOT_FOUND.name()));
        mockMvc.perform(get("/api/v1/orders/" + mine).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].commissionAmount").doesNotExist())
                .andExpect(jsonPath("$.sellerOrders[0].seller.displayName").value("Satıcı uretici"));
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Satıcı yalnızca kendi ödenmiş satıcı siparişlerini görür; ödeme bekleyen görünmez")
    void satici_listesi() throws Exception {
        var paid = paidOrder(buyer, buyerToken, seller, "odenen");
        var other = fixtures.approvedSeller("ikinci@ornek.test");
        fixtures.activePayoutAccount(other);
        paidOrder(buyer, buyerToken, other, "ikincinin");
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "bekleyen", 1_000, 5), 1);
        place(buyerToken, fixtures.address(buyer, "35"));

        mockMvc.perform(get("/api/v1/sellers/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(paid))
                .andExpect(jsonPath("$.items[0].status").value("NEW"))
                .andExpect(jsonPath("$.items[0].sellerPayout.amount").isNumber());
        mockMvc.perform(get("/api/v1/sellers/me/orders").param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Satıcı detayı: il ve ilçe var; alıcının adı, telefonu ve açık adresi yok")
    void satici_detayi_adres_gizliligi() throws Exception {
        paidOrder(buyer, buyerToken, seller, "gizli");
        var id = jdbc.queryForObject("select id from seller_orders", Long.class);

        var body = mockMvc.perform(get("/api/v1/sellers/me/orders/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinceName").value("İzmir"))
                .andExpect(jsonPath("$.district").value("Bornova"))
                .andExpect(jsonPath("$.commissionAmount.amount").isNumber())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Örnek Alıcı").doesNotContain("+905550000000").doesNotContain("Ornek Mahallesi");
    }

    @Test
    @DisplayName("Başka satıcının satıcı siparişi 404; askıdaki satıcı 403 SELLER_NOT_APPROVED; rolsüz 403")
    void satici_yetkisi() throws Exception {
        paidOrder(buyer, buyerToken, seller, "yetki");
        var id = jdbc.queryForObject("select id from seller_orders", Long.class);
        var other = fixtures.approvedSeller("ikinci@ornek.test");

        mockMvc.perform(get("/api/v1/sellers/me/orders/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_ORDER_NOT_FOUND.name()));
        jdbc.update("update sellers set status = 'SUSPENDED' where account_id = ?", seller);
        mockMvc.perform(get("/api/v1/sellers/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_APPROVED.name()));
        mockMvc.perform(get("/api/v1/sellers/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Satıcı listesi sipariş sayısından bağımsız sabit sayıda sorgu (≤ 3)")
    void sorgu_sayisi() throws Exception {
        for (int i = 0; i < 4; i++) {
            paidOrder(buyer, buyerToken, seller, "coklu-" + i);
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/v1/sellers/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.items.length()").value(4));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    /** Sepete ürün koyar, sipariş verir ve ödemeyi tamamlar; dönen değer sipariş numarası. */
    private String paidOrder(long buyerAccount, String token, long sellerAccount, String slug) throws Exception {
        fixtures.cartItem(buyerAccount, fixtures.publishedProduct(sellerAccount, slug, 2_450, 10), 1);
        var number = place(token, fixtures.address(buyerAccount, "35"));
        mockMvc.perform(post("/api/v1/orders/" + number + "/payment").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PAID"));
        return number;
    }

    private String place(String token, long addressId) throws Exception {
        var body = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "gorunum-" + KEYS.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("orderNumber").asText();
    }
}
