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
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Admin'in ayıplı ürün talebi kararı (defect-claims spec'i "Admin yönetime
 * taşınan talebi karara bağlar"; ADR-080, ADR-084, ADR-086).
 *
 * <p>{@code critical}: para (satın alınana kadar adetle iade), durum, yetki ve
 * kişisel veri. Sipariş: domates 3 × 24,50 + biber 2 × 10,00 = 93,50 TL; kargo
 * 99 TL; talep domates 2 adet.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Admin: ayıplı ürün talepleri /api/v1/admin/claims")
class AdminDefectClaimTest {

    private static final String ADMIN_URL = "/api/v1/admin/claims";

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private ListingFixtures fixtures;
    private long admin;
    private long buyer;
    private long seller;
    private String orderNumber;
    private long sellerOrder;
    private long claimId;
    private long tomato;
    private long pepper;

    @BeforeEach
    void setUp() throws Exception {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        integrator.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        var orders = new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
        var claims = new ClaimFlows(mockMvc, jdbc, orders, integrator, poller);
        admin = fixtures.account("admin@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_ADMIN");
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 3);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "biber", 1_000, 10), 2);
        orderNumber = orders.paidOrder(buyer, buyerToken());
        sellerOrder = orders.sellerOrderId(orderNumber);
        tomato = claims.itemId("domates");
        pepper = claims.itemId("biber");
        claims.deliver(fixtures.sellerToken(seller), sellerOrder);
        claimId = claims.open(buyerToken(), orderNumber, sellerOrder, Map.of(tomato, 2));
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        integrator.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Liste varsayılan olarak yönetimdekileri gösterir; alıcının kişisel verisi yok; sorgu sayısı sabit")
    void liste() throws Exception {
        escalate();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var body = mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(claimId))
                .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.items[0].sellerName").value("Satıcı uretici"))
                .andExpect(jsonPath("$.items[0].status").value("ESCALATED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        assertThat(body).doesNotContain("Örnek Alıcı").doesNotContain("+905550000000").doesNotContain("Ornek Mahallesi");

        mockMvc.perform(get(ADMIN_URL).param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(ADMIN_URL).param("status", "HUGE").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Oturum yoksa 401; admin olmayana 403")
    void yetki() throws Exception {
        mockMvc.perform(get(ADMIN_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_URL + "/" + claimId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\",\"reason\":\"Gerekçe on harf.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Detay: talep, fotoğraflar, satın alınan adetler; karar verilebilir; alıcının adı yok")
    void detay() throws Exception {
        escalate();

        var body = mockMvc.perform(get(ADMIN_URL + "/" + claimId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("ESCALATED"))
                .andExpect(jsonPath("$.claim.photos.length()").value(1))
                .andExpect(jsonPath("$.claim.sellerReason").value("Paket gönderilirken sağlamdı."))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.decidable").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Örnek Alıcı").doesNotContain("+905550000000");

        mockMvc.perform(get(ADMIN_URL + "/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("Admin alıcının istediğinden fazlasını, satın alınana kadar iade edebilir: 3 domates, 73,50 TL; geçmiş ve e-posta")
    void admin_iade() throws Exception {
        escalate();
        mail.reset();

        decide("{\"decision\":\"REFUND\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":3}],"
                + "\"reason\":\"Fotoğraflar kasanın ezik geldiğini gösteriyor.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("REFUNDED"))
                .andExpect(jsonPath("$.claim.refundAmount.amount").value(7_350))
                .andExpect(jsonPath("$.claim.lines[0].refundedQuantity").value(3))
                .andExpect(jsonPath("$.decidable").value(false));

        assertThat(provider.refunds()).singleElement().satisfies(refund -> assertThat(refund.amount()).isEqualTo(7_350));
        assertThat(jdbc.queryForMap("select resolved_by, commission_after, payout_after from defect_claims"))
                .containsEntry("resolved_by", "ADMIN")
                .containsEntry("commission_after", 400L)
                .containsEntry("payout_after", 1_600L);
        assertThat(jdbc.queryForMap("select actor_account_id, reason from defect_claim_changes order by id desc limit 1"))
                .containsEntry("actor_account_id", admin)
                .containsEntry("reason", "Fotoğraflar kasanın ezik geldiğini gösteriyor.");
        assertThat(mail.sent()).hasSize(2);
    }

    @Test
    @DisplayName("İade hataları: satın alınandan fazla adet, talepte olmayan satır, toplam sıfır; talep değişmez")
    void iade_hatalari() throws Exception {
        escalate();

        decide("{\"decision\":\"REFUND\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":4}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].quantity"));
        decide("{\"decision\":\"REFUND\",\"lines\":[{\"orderItemId\":" + pepper + ",\"quantity\":1}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].orderItemId"));
        decide("{\"decision\":\"REFUND\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines"));
        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("ESCALATED");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("Ret gerekçe ister; gerekçeli ret talebi kapatır, para hareketi yok, e-postalarda gerekçe var")
    void admin_ret() throws Exception {
        escalate();
        mail.reset();

        decide("{\"decision\":\"REJECT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        decide("{\"decision\":\"REJECT\",\"reason\":\"Fotoğraflar sorunu göstermiyor.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("CLOSED"));

        assertThat(provider.refunds()).isEmpty();
        assertThat(mail.sent()).hasSize(2)
                .allSatisfy(sent -> assertThat(sent.body()).contains("Fotoğraflar sorunu göstermiyor."));
    }

    @Test
    @DisplayName("Yönetimde olmayan talebe karar 409; önizleme yan etkisizdir")
    void yanlis_durum_ve_onizleme() throws Exception {
        decide("{\"decision\":\"REJECT\",\"reason\":\"Fotoğraflar sorunu göstermiyor.\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CLAIM_INVALID_TRANSITION.name()));

        mockMvc.perform(post(ADMIN_URL + "/" + claimId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount.amount").value(7_350))
                .andExpect(jsonPath("$.payoutAfter.amount").value(1_600));
        assertThat(jdbc.queryForObject("select status from defect_claims", String.class)).isEqualTo("OPEN");
        assertThat(provider.refunds()).isEmpty();
    }

    /** Satıcı reddeder, alıcı yönetime taşır. */
    private void escalate() throws Exception {
        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/claim/response")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reason\":\"Paket gönderilirken sağlamdı.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrder + "/claim/escalate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken()))
                .andExpect(status().isOk());
    }

    private ResultActions decide(String body) throws Exception {
        return mockMvc.perform(post(ADMIN_URL + "/" + claimId + "/decision")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String adminToken() {
        return fixtures.token(admin, "ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    private String buyerToken() {
        return fixtures.token(buyer, "ROLE_CUSTOMER");
    }
}
