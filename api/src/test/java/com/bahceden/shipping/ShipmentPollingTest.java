package com.bahceden.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.time.DateTimeFormat;
import com.bahceden.order.ShipmentProgressService;
import com.bahceden.shipping.domain.ShipmentStatus;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.ScriptedShippingIntegrator;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gönderi durumunun izlenmesi (shipments spec'i "Gönderi durumu entegratörden
 * izlenir"; add-shipments D2, D5, D8; add-delivered-email D1, D2).
 *
 * <p>{@code critical}: sipariş durumu geçişleri. Zamanlayıcı testlerde kapalıdır;
 * sorgu sabit saatle doğrudan çağrılır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Gönderi durumu sorgusu")
class ShipmentPollingTest {

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
    private RecordingMailSender mail;

    @Autowired
    private ShipmentStatusPoller poller;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ShipmentProgressService progress;

    @Autowired
    private TransactionTemplate transactions;

    private ListingFixtures fixtures;
    private OrderFlows flows;
    private long buyer;
    private String buyerToken;
    private long seller;
    private String sellerToken;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        integrator.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        flows = new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
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
        integrator.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Kargoya verildi: SHIPPED, kargoya verilme zamanı, sistem geçişi ve alıcıya İngilizce e-posta")
    void kargoya_verildi() throws Exception {
        var shipment = openedShipment();
        mail.reset();
        integrator.report(shipment.ref(), ShipmentStatus.HANDED_OVER);

        assertThat(poller.pollOnce()).isEqualTo(1);

        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("SHIPPED");
        assertThat(jdbc.queryForMap("select status, handed_over_at from shipments"))
                .containsEntry("status", "HANDED_OVER")
                .satisfies(row -> assertThat(row.get("handed_over_at")).isNotNull());
        assertThat(jdbc.queryForMap("""
                select to_status, actor_account_id from order_status_changes order by id desc limit 1"""))
                .containsEntry("to_status", "SHIPPED")
                .containsEntry("actor_account_id", null);
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.subject()).contains("shipped", shipment.number());
            assertThat(sent.body()).contains("Test Kargo", "TK00000001");
        });
        mockMvc.perform(get("/api/v1/orders/" + shipment.number())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.sellerOrders[0].status").value("SHIPPED"))
                .andExpect(jsonPath("$.sellerOrders[0].cancellable").value(false))
                .andExpect(jsonPath("$.sellerOrders[0].shipment.trackingNumber").value("TK00000001"))
                .andExpect(jsonPath("$.sellerOrders[0].shipment.status").value("HANDED_OVER"));
    }

    @Test
    @DisplayName("Yolda bildirimi satıcı siparişini değiştirmez; teslim edildi: DELIVERED ve teslim zamanı")
    void yolda_ve_teslim() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.HANDED_OVER);
        poller.pollOnce();
        integrator.report(shipment.ref(), ShipmentStatus.IN_TRANSIT);
        poller.pollOnce();
        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("SHIPPED");

        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);
        poller.pollOnce();

        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select delivered_at is not null from shipments", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("select to_status from order_status_changes order by id", String.class))
                .containsExactly("PENDING_PAYMENT", "NEW", "SHIPPED", "DELIVERED");
    }

    @Test
    @DisplayName("Teslim edildi: yalnızca alıcıya, hesabının dilinde, sorun bildiriminin son anı ve sipariş bağlantısıyla e-posta")
    void teslim_edildi_epostasi() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.HANDED_OVER);
        poller.pollOnce();
        mail.reset();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);

        poller.pollOnce();

        var deadline = clock.instant().plus(Duration.ofHours(48));
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.subject()).contains("delivered", shipment.number());
            assertThat(sent.body()).contains(
                    DateTimeFormat.format(deadline, SupportedLocale.EN) + " Turkey time",
                    "48 hours",
                    "/account/orders/" + shipment.number());
        });
    }

    @Test
    @DisplayName("E-postadaki son an, alıcının sipariş detayındaki talep son anıyla aynıdır")
    void eposta_ve_ekran_ayni_ani_gosterir() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);
        poller.pollOnce();

        var json = mockMvc.perform(get("/api/v1/orders/" + shipment.number())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var claimableUntil = Instant.parse(objectMapper.readTree(json)
                .at("/sellerOrders/0/claimableUntil").asText());

        assertThat(mail.lastSent().body()).contains(DateTimeFormat.format(claimableUntil, SupportedLocale.EN));
    }

    @Test
    @DisplayName("Ara durum atlanırsa (CREATED → DELIVERED) iki geçiş sırayla yazılır; önce kargoya verildi, sonra teslim edildi e-postası")
    void ara_durum_atlanir() throws Exception {
        var shipment = openedShipment();
        mail.reset();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);

        poller.pollOnce();

        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList("select to_status from order_status_changes order by id", String.class))
                .containsExactly("PENDING_PAYMENT", "NEW", "SHIPPED", "DELIVERED");
        assertThat(jdbc.queryForObject("select handed_over_at is not null from shipments", Boolean.class)).isTrue();
        assertThat(mail.sent()).extracting(RecordingMailSender.SentMail::subject)
                .satisfiesExactly(
                        subject -> assertThat(subject).contains("shipped"),
                        subject -> assertThat(subject).contains("delivered"));
    }

    @Test
    @DisplayName("Teslim bildirimi yinelenirse geçmiş değişmez ve ikinci e-posta gitmez")
    void yinelenen_teslim_bildirimi() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);
        poller.pollOnce();
        mail.reset();

        transactions.executeWithoutResult(status -> progress.delivered(shipment.sellerOrder(), clock.instant()));

        assertThat(mail.sent()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from order_status_changes where to_status = 'DELIVERED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Teslim edilemedi: DELIVERY_FAILED, alıcıya ve satıcıya e-posta")
    void teslim_edilemedi() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.HANDED_OVER);
        poller.pollOnce();
        mail.reset();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERY_FAILED);

        poller.pollOnce();

        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("DELIVERY_FAILED");
        assertThat(mail.sent()).extracting(RecordingMailSender.SentMail::to)
                .containsExactlyInAnyOrder("alici@ornek.test", "uretici@ornek.test");
    }

    @Test
    @DisplayName("Geri giden veya aynı bildirim yok sayılır; kapanmış gönderi sorgulanmaz")
    void geri_giden_bildirim() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.IN_TRANSIT);
        poller.pollOnce();
        integrator.report(shipment.ref(), ShipmentStatus.HANDED_OVER);

        assertThat(poller.pollOnce()).isZero();
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject("select count(*) from order_status_changes where to_status = 'SHIPPED'",
                Integer.class)).isEqualTo(1);

        integrator.report(shipment.ref(), ShipmentStatus.DELIVERED);
        poller.pollOnce();
        integrator.report(shipment.ref(), ShipmentStatus.DELIVERY_FAILED);
        assertThat(poller.pollOnce()).isZero();
        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("Bir gönderinin sorgusu başarısız olursa o değişmez, diğeri güncellenir")
    void hata_izolasyonu() throws Exception {
        var first = openedShipment();
        var otherSeller = fixtures.approvedSeller("ikinci@ornek.test");
        fixtures.activePayoutAccount(otherSeller);
        fixtures.cartItem(buyer, fixtures.publishedProduct(otherSeller, "kiraz", 5_000, 10), 1);
        var number = flows.paidOrder(buyer, buyerToken);
        var secondOrder = flows.sellerOrderId(number);
        var secondRef = flows.openShipment(fixtures.sellerToken(otherSeller), secondOrder);
        integrator.failStatusOf(first.ref());
        integrator.report(first.ref(), ShipmentStatus.HANDED_OVER);
        integrator.report(secondRef, ShipmentStatus.HANDED_OVER);

        assertThat(poller.pollOnce()).isEqualTo(1);

        assertThat(sellerOrderStatus(first.sellerOrder())).isEqualTo("NEW");
        assertThat(sellerOrderStatus(secondOrder)).isEqualTo("SHIPPED");
        assertThat(jdbc.queryForObject("select last_polled_at is not null from shipments where integrator_ref = ?",
                Boolean.class, first.ref())).isTrue();
    }

    @Test
    @DisplayName("İptal edilmiş sipariş için entegratörden CANCELLED gelirse gönderi CANCELLED olur")
    void iptal_bildirimi() throws Exception {
        var shipment = openedShipment();
        integrator.report(shipment.ref(), ShipmentStatus.CANCELLED);

        poller.pollOnce();

        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CANCELLED");
        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("NEW");
    }

    @Test
    @DisplayName("İade başarısız olursa iptal geri alınır; entegratörde iptal edilen gönderi sonraki sorguda CANCELLED")
    void iade_basarisiz_gonderi_sonradan_iptal() throws Exception {
        var shipment = openedShipment();
        provider.failRefunds();

        mockMvc.perform(post("/api/v1/orders/" + shipment.number() + "/seller-orders/" + shipment.sellerOrder() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CREATED");

        poller.pollOnce();

        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CANCELLED");
        assertThat(sellerOrderStatus(shipment.sellerOrder())).isEqualTo("NEW");
        flows.createShipment(sellerToken, shipment.sellerOrder(), "{\"packageSize\":\"SMALL\"}")
                .andExpect(status().isCreated());
        assertThat(integrator.created()).last()
                .satisfies(request -> assertThat(request.reference()).endsWith("-2"));
    }

    @Test
    @DisplayName("Testlerde zamanlayıcı kurulmaz (polling-enabled=false)")
    void zamanlayici_kapali() {
        assertThat(context.getBeanNamesForType(ShipmentPollingSchedule.class)).isEmpty();
    }

    private record Opened(String number, long sellerOrder, String ref) {
    }

    private Opened openedShipment() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = flows.paidOrder(buyer, buyerToken);
        var sellerOrder = flows.sellerOrderId(number);
        return new Opened(number, sellerOrder, flows.openShipment(sellerToken, sellerOrder));
    }

    private String sellerOrderStatus(long sellerOrderId) {
        return jdbc.queryForObject("select status from seller_orders where id = ?", String.class, sellerOrderId);
    }
}
