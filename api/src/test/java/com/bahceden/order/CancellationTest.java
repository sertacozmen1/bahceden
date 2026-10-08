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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kargodan önce iptal ve iade (orders spec'i "Kargoya verilmemiş satıcı
 * siparişi iptal edilir ve tamamı iade edilir"; ADR-056 §1–2, D9).
 *
 * <p>{@code critical}: para (iade tutarı), stok (geri ekleme), sipariş durumu
 * ve sahiplik.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Kargodan önce iptal: alıcı ve satıcı")
class CancellationTest {

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
    private RecordingMailSender mail;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;
    private long seller;
    private String sellerToken;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "en", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        sellerToken = fixtures.sellerToken(seller);
        fixtures.activePayoutAccount(seller);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Alıcı iptali: CANCELLED, ürün + kargo iade, stok geri eklenir, geçmiş ve satıcıya e-posta")
    void alici_iptali() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomato, 3);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        mail.reset();

        buyerCancel(number, sellerOrder, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.sellerOrders[0].cancelledBy").value("BUYER"))
                .andExpect(jsonPath("$.sellerOrders[0].cancellable").value(false));

        assertThat(stockOf(tomato)).isEqualTo(10);
        assertThat(provider.refunds()).singleElement().satisfies(refund -> {
            assertThat(refund.amount()).isEqualTo(3 * 2_450 + 9_900);
            assertThat(refund.subMerchantKey()).contains("test-sub-seller-" + seller);
        });
        assertThat(jdbc.queryForMap("select reason, seller_order_id, amount from refunds"))
                .containsEntry("reason", "CANCELLED")
                .containsEntry("seller_order_id", sellerOrder)
                .containsEntry("amount", 3L * 2_450 + 9_900);
        assertThat(jdbc.queryForList("select to_status from order_status_changes order by id", String.class))
                .containsExactly("PENDING_PAYMENT", "NEW", "CANCELLED");
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("uretici@ornek.test");
            assertThat(sent.subject()).contains("sipariş iptal edildi", number);
        });
    }

    @Test
    @DisplayName("Satıcı iptali gerekçe ister (yok veya kısa: 400 reason); gerekçeyle iptal alıcıya gerekçeli e-posta")
    void satici_iptali() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        mail.reset();

        sellerCancel(sellerOrder, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        sellerCancel(sellerOrder, "{\"reason\":\"kısa\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));

        sellerCancel(sellerOrder, "{\"reason\":\"Hasat beklenenden az oldu.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("SELLER"))
                .andExpect(jsonPath("$.cancelReason").value("Hasat beklenenden az oldu."));

        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.body()).contains("Hasat beklenenden az oldu.").contains(number);
        });
    }

    @Test
    @DisplayName("İki satıcılı siparişte yalnızca biri iptal edilir; diğeri ve ödemesi değişmez")
    void diger_satici_etkilenmez() throws Exception {
        var other = fixtures.approvedSeller("ikinci@ornek.test");
        fixtures.activePayoutAccount(other);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        fixtures.cartItem(buyer, fixtures.publishedProduct(other, "kiraz", 5_000, 10), 1);
        var number = paidOrder();
        var first = jdbc.queryForObject("select id from seller_orders where seller_id = ?", Long.class,
                fixtures.sellerIdOf(seller));

        buyerCancel(number, first, "{\"reason\":\"Vazgeçtim.\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForList("select status from seller_orders order by id", String.class))
                .containsExactly("CANCELLED", "NEW");
        assertThat(provider.refunds()).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(2_450 + 9_900));
    }

    @Test
    @DisplayName("İptal edilmiş veya ödenmemiş satıcı siparişi tekrar iptal edilemez: 409 ORDER_INVALID_TRANSITION")
    void iptal_edilemeyen_durum() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        buyerCancel(number, sellerOrder, "{}").andExpect(status().isOk());

        buyerCancel(number, sellerOrder, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));

        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "biber", 1_000, 10), 1);
        var pending = place();
        buyerCancel(pending, sellerOrderId(pending), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));
        assertThat(provider.refunds()).hasSize(1);
    }

    @Test
    @DisplayName("Başkasının siparişi: alıcıya 404 ORDER_NOT_FOUND, başka satıcıya 404 SELLER_ORDER_NOT_FOUND")
    void baskasinin_siparisi() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        var stranger = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");
        var otherSeller = fixtures.approvedSeller("ikinci@ornek.test");

        mockMvc.perform(post("/api/v1/orders/" + number + "/seller-orders/" + sellerOrder + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.token(stranger, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_NOT_FOUND.name()));
        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(otherSeller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Başkasının siparişi.\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_ORDER_NOT_FOUND.name()));
        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("NEW");
    }

    @Test
    @DisplayName("İade başarısız olursa hiçbir şey değişmez: 502, durum NEW, stok ve geçmiş aynı")
    void iade_basarisiz() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomato, 2);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        provider.failRefunds();
        mail.reset();

        buyerCancel(number, sellerOrder, "{}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYMENT_PROVIDER_ERROR.name()));

        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("NEW");
        assertThat(stockOf(tomato)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from refunds", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_status_changes", Integer.class)).isEqualTo(2);
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Son tarih geçmişken alıcı yine iptal eder; detay süre aşımını gösterir")
    void sure_asiminda_iptal() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = paidOrder();
        var sellerOrder = sellerOrderId(number);
        clock.advance(Duration.ofDays(8));
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/v1/orders/" + number)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.sellerOrders[0].dispatchOverdue").value(true));
        buyerCancel(number, sellerOrder, "{}").andExpect(status().isOk());
    }

    private String place() throws Exception {
        var body = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", "iptal-anahtari-" + KEYS.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", fixtures.address(buyer, "35")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("orderNumber").asText();
    }

    private String paidOrder() throws Exception {
        var number = place();
        mockMvc.perform(post("/api/v1/orders/" + number + "/payment").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.status").value("PAID"));
        return number;
    }

    private long sellerOrderId(String orderNumber) {
        return jdbc.queryForObject("""
                select so.id from seller_orders so join orders o on o.id = so.order_id
                where o.order_number = ? order by so.id limit 1""", Long.class, orderNumber);
    }

    private ResultActions buyerCancel(String orderNumber, long sellerOrderId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions sellerCancel(long sellerOrderId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, productId);
    }
}
