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
    private OrderFlows orders;
    private ClaimFlows claims;
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
        orders = new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
        claims = new ClaimFlows(mockMvc, jdbc, orders, integrator, poller);
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
    @DisplayName("Satıcı 48 saatte yanıt vermezse talep yönetime düşer; eylemcisiz geçmiş ve iki tarafa e-posta (admin hesabı yokken de)")
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
    @DisplayName("Satıcının süresi dolunca her admin'e kendi dilinde, nedeni ve talep bağlantısıyla e-posta gider (ADR-101)")
    void satici_sessiz_admin_eposta() {
        fixtures.account("yonetici@ornek.test", "tr", "ROLE_ADMIN");
        fixtures.account("admin-en@ornek.test", "en", "ROLE_ADMIN");
        clock.advance(PAST_DEADLINE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(mail.sent()).hasSize(4);
        assertThat(mail.onlySentTo("yonetici@ornek.test").body())
                .contains("üretici süresinde yanıt vermedi")
                .contains(orderNumber)
                .contains("/admin/claims/" + claimId);
        assertThat(mail.onlySentTo("admin-en@ornek.test").body())
                .contains("the producer did not respond in time")
                .contains("/admin/claims/" + claimId);
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
    @DisplayName("İadesi başarısız en eski talepler arkadakini bekletmez: sayfa boyutu 2, üçüncü talep aynı turda işlenir; her talep bir kez denenir (ADR-100)")
    void basarisiz_talepler_arkadakini_bekletmez() throws Exception {
        sellerRespond("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + tomato + ",\"quantity\":1}]}");
        var second = offeredClaimOn("biber");
        var third = offeredClaimOn("patlican");
        clock.advance(PAST_DEADLINE);
        // Test sayfa boyutu 2'dir: başarısız iki talep ilk sayfayı doldurur.
        provider.failRefundsOf(paymentReference(orderNumber));
        provider.failRefundsOf(paymentReference(second.orderNumber()));

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(claimStatus()).isEqualTo("OFFERED");
        assertThat(statusOf(second.claimId())).isEqualTo("OFFERED");
        assertThat(statusOf(third.claimId())).isEqualTo("REFUNDED");
        assertThat(provider.refundAttemptsOf(paymentReference(orderNumber))).isEqualTo(1);
        assertThat(provider.refundAttemptsOf(paymentReference(second.orderNumber()))).isEqualTo(1);

        provider.reset();
        assertThat(sweep.sweepOnce()).isEqualTo(2);
        assertThat(claimStatus()).isEqualTo("REFUNDED");
        assertThat(statusOf(second.claimId())).isEqualTo("REFUNDED");
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

    private record OfferedClaim(String orderNumber, long claimId) {
    }

    /** Aynı alıcı ve satıcıyla, bir dakika sonra: sipariş, teslim, 2 adetlik talep ve 1 adetlik kısmi teklif. */
    private OfferedClaim offeredClaimOn(String slug) throws Exception {
        clock.advance(Duration.ofMinutes(1));
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, slug, 1_500, 10), 2);
        var number = orders.paidOrder(buyer, buyerToken());
        var otherSellerOrder = orders.sellerOrderId(number);
        claims.deliver(sellerToken(), otherSellerOrder);
        var item = claims.itemId(slug);
        var id = claims.open(buyerToken(), number, otherSellerOrder, Map.of(item, 2));
        mockMvc.perform(post("/api/v1/sellers/me/orders/" + otherSellerOrder + "/claim/response")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"OFFER\",\"lines\":[{\"orderItemId\":" + item + ",\"quantity\":1}]}"))
                .andExpect(status().isOk());
        return new OfferedClaim(number, id);
    }

    private String paymentReference(String number) {
        return jdbc.queryForObject("""
                select p.provider_reference from payments p join orders o on o.id = p.order_id
                where o.order_number = ?""", String.class, number);
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("select status from defect_claims where id = ?", String.class, id);
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
