package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.order.domain.DefectClaimStatus;
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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * Talep süreleri (defect-claims spec'i "Süresi dolan talepler kendiliğinden
 * ilerler"; ADR-083). Süre işi zamanlayıcısız, sabit saatle doğrudan çağrılır.
 *
 * <p>{@code critical}: zamanlanmış iş para hareket ettirir; çift iade olmaması
 * ve iade hatasında talebin değişmemesi burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Ayıplı ürün talebi süre işi")
class DefectClaimSweepTest {

    private static final Duration PAST_DEADLINE = Duration.ofHours(49);

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
    private DefectClaimSweep sweep;

    @Autowired
    private DefectClaimService claimService;

    private ListingFixtures fixtures;
    private long buyer;
    private long seller;
    private String orderNumber;
    private long sellerOrder;
    private long claimId;
    private long tomato;

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
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 3);
        orderNumber = orders.paidOrder(buyer, buyerToken());
        sellerOrder = orders.sellerOrderId(orderNumber);
        tomato = claims.itemId("domates");
        claims.deliver(sellerToken(), sellerOrder);
        claimId = claims.open(buyerToken(), orderNumber, sellerOrder, Map.of(tomato, 2));
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
    @DisplayName("Süre dolmadan hiçbir talebe dokunulmaz")
    void sure_dolmadan() {
        clock.advance(Duration.ofHours(47));

        assertThat(sweep.sweepOnce()).isZero();
        assertThat(claimStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Satıcı 48 saatte yanıt vermezse talep yönetime düşer; eylemcisiz geçmiş ve iki tarafa e-posta")
    void satici_sessiz() {
        clock.advance(PAST_DEADLINE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(claimStatus()).isEqualTo("ESCALATED");
        assertThat(jdbc.queryForMap("select from_status, to_status, actor_account_id from defect_claim_changes order by id desc limit 1"))
                .containsEntry("from_status", "OPEN")
                .containsEntry("to_status", "ESCALATED")
                .containsEntry("actor_account_id", null);
        assertThat(mail.sent()).hasSize(2);
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("Alıcı teklife 48 saatte yanıt vermezse teklif uygulanır: önerilen adet iade, DEADLINE ile kapanır")
    void alici_teklife_sessiz() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        clock.advance(PAST_DEADLINE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(claimStatus()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select resolved_by from defect_claims", String.class)).isEqualTo("DEADLINE");
        assertThat(provider.refunds()).singleElement().satisfies(refund -> assertThat(refund.amount()).isEqualTo(2_450));
        assertThat(jdbc.queryForObject("select refunded_quantity from defect_claim_lines", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Alıcı redde 48 saatte yanıt vermezse talep iadesiz kapanır")
    void alici_redde_sessiz() throws Exception {
        sellerRespond("{\"decision\":\"REJECT\",\"reason\":\"Paket gönderilirken sağlamdı.\"}");
        clock.advance(PAST_DEADLINE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(claimStatus()).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select resolved_by from defect_claims", String.class)).isEqualTo("DEADLINE");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("İade yapılamazsa talep OFFERED kalır; sağlayıcı düzelince sonraki turda tek iade yapılır")
    void iade_hatasi_sonraki_turda() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        clock.advance(PAST_DEADLINE);
        provider.failRefunds();

        assertThat(sweep.sweepOnce()).isZero();
        assertThat(claimStatus()).isEqualTo("OFFERED");
        assertThat(jdbc.queryForObject("select count(*) from refunds", Integer.class)).isZero();

        provider.reset();
        assertThat(sweep.sweepOnce()).isEqualTo(1);
        assertThat(claimStatus()).isEqualTo("REFUNDED");
        assertThat(provider.refunds()).hasSize(1);
        assertThat(sweep.sweepOnce()).isZero();
    }

    @Test
    @DisplayName("Alıcı kabul ettikten sonra süre işi talebe dokunmaz; süre işinden sonra alıcı eylemi 409")
    void sirali_eylemler() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        clock.advance(PAST_DEADLINE);
        buyerAccept().andExpect(status().isOk());

        assertThat(claimService.expire(claimId)).isEmpty();
        assertThat(sweep.sweepOnce()).isZero();
        assertThat(provider.refunds()).hasSize(1);
    }

    @Test
    @DisplayName("Süre işi teklifi uyguladıktan sonra alıcının kabulü 409 alır; ikinci iade yok")
    void sure_isinden_sonra_alici() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        clock.advance(PAST_DEADLINE);
        assertThat(sweep.sweepOnce()).isEqualTo(1);

        buyerAccept().andExpect(status().isConflict());
        assertThat(provider.refunds()).hasSize(1);
    }

    @Test
    @DisplayName("Alıcının kabulü ile süre işi aynı anda çalışırsa yalnızca bir iade yapılır")
    void eszamanli_tek_iade() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        clock.advance(PAST_DEADLINE);
        var token = buyerToken();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var bySweep = executor.submit(() -> {
                start.await();
                return claimService.expire(claimId);
            });
            var byBuyer = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrder
                                + "/claim/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            var sweepResult = bySweep.get(30, TimeUnit.SECONDS);
            var buyerStatus = byBuyer.get(30, TimeUnit.SECONDS);

            assertThat(provider.refunds()).hasSize(1);
            assertThat(claimStatus()).isEqualTo("REFUNDED");
            assertThat(buyerStatus == 200 ^ sweepResult.equals(Optional.of(DefectClaimStatus.REFUNDED)))
                    .as("tam olarak biri iadeyi yapar")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private String buyerToken() {
        return fixtures.token(buyer, "ROLE_CUSTOMER");
    }

    private String sellerToken() {
        return fixtures.sellerToken(seller);
    }

    private String claimStatus() {
        return jdbc.queryForObject("select status from defect_claims where id = ?", String.class, claimId);
    }

    private void sellerRespond(String body) throws Exception {
        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/claim/response")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private ResultActions buyerAccept() throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrder + "/claim/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken()));
    }
}
