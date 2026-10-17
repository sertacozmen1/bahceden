package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.shipping.ShipmentStatusPoller;
import com.bahceden.support.ClaimFlows;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.ScriptedShippingIntegrator;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Satıcının yanıtı, alıcının yanıtı ve iade (defect-claims spec'i "Satıcı
 * talebi kabul eder…", "Alıcı teklifi veya reddi kabul eder…", "İade tutarı
 * ve yeni hakediş tek kuralla hesaplanır"; ADR-080, ADR-082).
 *
 * <p>{@code critical}: para (iade tutarı, komisyon, hakediş), durum geçişleri ve
 * sahiplik. Sipariş: domates 3 × 24,50 + biber 2 × 10,00 = 93,50 TL; kargo
 * 99 TL; komisyon %5 + 3 TL = 7,68 TL.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Ayıplı ürün talebine yanıt ve iade")
class DefectClaimResponseTest {

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
    private ScriptedShippingIntegrator integrator;

    @Autowired
    private ShipmentStatusPoller poller;

    @Autowired
    private RecordingMailSender mail;

    private ListingFixtures fixtures;
    private ClaimFlows claims;
    private long buyer;
    private String buyerToken;
    private long seller;
    private String sellerToken;
    private String orderNumber;
    private long sellerOrder;
    private long tomato;
    private long pepper;
    private long tomatoProduct;

    @BeforeEach
    void setUp() throws Exception {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        integrator.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        var orders = new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
        claims = new ClaimFlows(mockMvc, jdbc, orders, integrator, poller);
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        sellerToken = fixtures.sellerToken(seller);
        fixtures.activePayoutAccount(seller);
        tomatoProduct = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomatoProduct, 3);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "biber", 1_000, 10), 2);
        orderNumber = orders.paidOrder(buyer, buyerToken);
        sellerOrder = orders.sellerOrderId(orderNumber);
        tomato = claims.itemId("domates");
        pepper = claims.itemId("biber");
        claims.deliver(sellerToken, sellerOrder);
        mail.reset();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        integrator.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Satıcı kabul eder: 2 domates iade (49 TL), komisyon 5,23 TL, hakediş 39,27 TL; stok değişmez; iki tarafa e-posta")
    void satici_kabul_eder() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));
        mail.reset();

        respond("{\"decision\":\"ACCEPT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("REFUNDED"))
                .andExpect(jsonPath("$.claim.refundAmount.amount").value(4_900))
                .andExpect(jsonPath("$.claim.shippingRefunded").value(false))
                .andExpect(jsonPath("$.claim.lines[0].refundedQuantity").value(2))
                .andExpect(jsonPath("$.commissionAfterClaim.amount").value(523))
                .andExpect(jsonPath("$.payoutAfterClaim.amount").value(3_927))
                .andExpect(jsonPath("$.sellerPayout.amount").value(9_350 - 768));

        assertThat(provider.refunds()).singleElement().satisfies(refund -> {
            assertThat(refund.amount()).isEqualTo(4_900);
            assertThat(refund.subMerchantKey()).contains("test-sub-seller-" + seller);
        });
        assertThat(jdbc.queryForMap("select reason, amount from refunds"))
                .containsEntry("reason", "DEFECT")
                .containsEntry("amount", 4_900L);
        assertThat(jdbc.queryForMap("select resolved_by, commission_after, payout_after from defect_claims"))
                .containsEntry("resolved_by", "SELLER")
                .containsEntry("commission_after", 523L)
                .containsEntry("payout_after", 3_927L);
        assertThat(jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, tomatoProduct))
                .isEqualTo(7);
        assertThat(mail.sent()).hasSize(2).anySatisfy(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.body()).contains("49,00");
        }).anySatisfy(sent -> {
            assertThat(sent.to()).isEqualTo("uretici@ornek.test");
            assertThat(sent.body()).contains("49,00").contains("39,27");
        });
        mockMvc.perform(get("/api/v1/orders/" + orderNumber).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.sellerOrders[0].claim.status").value("REFUNDED"))
                .andExpect(jsonPath("$.sellerOrders[0].status").value("DELIVERED"));
    }

    @Test
    @DisplayName("Her satırın bütün adetleri kabul edilirse tam iade: ürün + kargo, hakediş sıfır")
    void tam_iade() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 3, pepper, 2));

        respond("{\"decision\":\"ACCEPT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.refundAmount.amount").value(9_350 + 9_900))
                .andExpect(jsonPath("$.claim.shippingRefunded").value(true))
                .andExpect(jsonPath("$.payoutAfterClaim.amount").value(0));

        assertThat(provider.refunds()).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(19_250));
    }

    @Test
    @DisplayName("Satıcı teklif verir; alıcı 48 saatlik süreyle e-posta alır; kabul edince teklif edilen adet iade edilir")
    void teklif_ve_alici_kabulu() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, ordered(tomato, 2, pepper, 1));
        mail.reset();

        respond(offer(tomato, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("OFFERED"))
                .andExpect(jsonPath("$.claim.lines[0].offeredQuantity").value(1))
                .andExpect(jsonPath("$.claim.lines[1].offeredQuantity").value(0))
                .andExpect(jsonPath("$.payoutAfterClaim").doesNotExist());
        assertThat(provider.refunds()).isEmpty();
        assertThat(((Timestamp) jdbc.queryForObject("select buyer_deadline_at from defect_claims", Timestamp.class))
                .toInstant()).isEqualTo(clock.instant().plus(Duration.ofHours(48)));
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.body()).contains("24,50").contains("48 saat");
        });

        buyerAction("accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].claim.status").value("REFUNDED"))
                .andExpect(jsonPath("$.sellerOrders[0].claim.refundAmount.amount").value(2_450))
                .andExpect(jsonPath("$.sellerOrders[0].claim.lines[0].refundedQuantity").value(1))
                .andExpect(jsonPath("$.sellerOrders[0].claim.lines[1].refundedQuantity").value(0));
        assertThat(jdbc.queryForObject("select resolved_by from defect_claims", String.class)).isEqualTo("BUYER");
        assertThat(provider.refunds()).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(2_450));
    }

    @Test
    @DisplayName("Bütün satırlarda istenen adeti önermek kabuldür; doğrudan iade edilir")
    void istenen_kadar_teklif_kabuldur() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));

        respond(offer(tomato, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("REFUNDED"));
        assertThat(jdbc.queryForObject("select resolved_by from defect_claims", String.class)).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("Teklif hataları: istenenden fazla, talepte olmayan satır, toplam sıfır; talep değişmez")
    void teklif_hatalari() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));

        respond(offer(tomato, 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].quantity"));
        respond(offer(pepper, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].orderItemId"));
        respond(offer(tomato, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines"));
        respond("{\"decision\":\"MAYBE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("decision"));
        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Ret gerekçe ister; gerekçeli ret alıcıya iletilir; alıcı kabul edince talep iadesiz kapanır")
    void ret_ve_kapanis() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));
        mail.reset();

        respond("{\"decision\":\"REJECT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        respond("{\"decision\":\"REJECT\",\"reason\":\"Paket gönderilirken sağlamdı.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("REJECTED"))
                .andExpect(jsonPath("$.claim.sellerReason").value("Paket gönderilirken sağlamdı."));
        assertThat(mail.sent()).singleElement()
                .satisfies(sent -> assertThat(sent.body()).contains("Paket gönderilirken sağlamdı."));
        mail.reset();

        buyerAction("accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].claim.status").value("CLOSED"));
        assertThat(provider.refunds()).isEmpty();
        assertThat(mail.sent()).hasSize(2);
    }

    @Test
    @DisplayName("Alıcı reddi yönetime taşır: ESCALATED ve iki tarafa e-posta; ikinci kez 409")
    void yonetime_tasima() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));
        respond("{\"decision\":\"REJECT\",\"reason\":\"Paket gönderilirken sağlamdı.\"}").andExpect(status().isOk());
        mail.reset();

        buyerAction("escalate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].claim.status").value("ESCALATED"));
        assertThat(mail.sent()).hasSize(2);
        buyerAction("escalate")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_INVALID_TRANSITION.name()));
    }

    @Test
    @DisplayName("Yanlış durumda eylem 409: açık talepte alıcı kabulü, yanıtlanmış talebe ikinci satıcı yanıtı")
    void yanlis_durum() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));

        buyerAction("accept")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_INVALID_TRANSITION.name()));
        respond(offer(tomato, 1)).andExpect(status().isOk());
        respond("{\"decision\":\"ACCEPT\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_INVALID_TRANSITION.name()));
    }

    @Test
    @DisplayName("Başka satıcı ve başka alıcı talebe dokunamaz: 404 CLAIM_NOT_FOUND")
    void sahiplik() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));
        var otherSeller = fixtures.approvedSeller("ikinci@ornek.test");
        var stranger = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");

        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/claim/response")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(otherSeller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_NOT_FOUND.name()));
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrder + "/claim/escalate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.token(stranger, "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_NOT_FOUND.name()));
        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("İade başarısızsa hiçbir şey değişmez: 502, talep OPEN, iade kaydı ve yeni geçmiş yok")
    void iade_basarisiz() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));
        provider.failRefunds();
        mail.reset();

        respond("{\"decision\":\"ACCEPT\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYMENT_PROVIDER_ERROR.name()));

        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select count(*) from refunds", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from defect_claim_changes", Integer.class)).isEqualTo(1);
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Önizleme yan etkisizdir: satırsız istek tamamını kabulü, satırlı istek teklifi hesaplar")
    void onizleme() throws Exception {
        claims.open(buyerToken, orderNumber, sellerOrder, Map.of(tomato, 2));

        preview("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount.amount").value(4_900))
                .andExpect(jsonPath("$.payoutAfter.amount").value(3_927));
        preview("{\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount.amount").value(2_450))
                .andExpect(jsonPath("$.commissionAfter.amount").value(645))
                .andExpect(jsonPath("$.payoutAfter.amount").value(6_255))
                .andExpect(jsonPath("$.shippingRefunded").value(false));

        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("OPEN");
        assertThat(provider.refunds()).isEmpty();
    }

    private static Map<Long, Integer> ordered(long firstItem, int firstQuantity, long secondItem, int secondQuantity) {
        var quantities = new LinkedHashMap<Long, Integer>();
        quantities.put(firstItem, firstQuantity);
        quantities.put(secondItem, secondQuantity);
        return quantities;
    }

    private static String offer(long orderItemId, int quantity) {
        return "{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + orderItemId + ",\"quantity\":" + quantity + "}]}";
    }

    private ResultActions respond(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/claim/response")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions preview(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/claim/preview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions buyerAction(String action) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrder + "/claim/" + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken));
    }
}
