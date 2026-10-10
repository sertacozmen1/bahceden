package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.shipping.ShipmentStatusPoller;
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
 * Teslim edilemeyen satıcı siparişinde admin kararı (shipments spec'i "Teslim
 * edilemeyen siparişte admin karar verir"; add-shipments D7).
 *
 * <p>{@code critical}: para (iade tutarı), stok (geri eklenmez), sipariş
 * durumu ve yetkilendirme.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Teslim edilemeyen sipariş: admin kararı")
class UndeliveredResolutionTest {

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

    private ListingFixtures fixtures;
    private OrderFlows flows;
    private long buyer;
    private String buyerToken;
    private long seller;
    private String sellerToken;
    private long admin;
    private String adminToken;
    private long tomato;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        integrator.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        flows = new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        sellerToken = fixtures.sellerToken(seller);
        fixtures.activePayoutAccount(seller);
        admin = fixtures.account("admin@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_ADMIN");
        adminToken = fixtures.token(admin, "ROLE_CUSTOMER", "ROLE_ADMIN");
        tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        integrator.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Liste ve detay: karar bekleyen görünür; detayda alıcı adı ve telefonu var, açık adres yok")
    void liste_ve_detay() throws Exception {
        var sellerOrder = undeliveredSellerOrder();

        mockMvc.perform(get("/api/v1/admin/seller-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(sellerOrder))
                .andExpect(jsonPath("$.items[0].refundableAmount.amount").value(2 * 2_450 + 9_900));
        mockMvc.perform(get("/api/v1/admin/seller-orders/" + sellerOrder)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Örnek Alıcı"))
                .andExpect(jsonPath("$.recipientPhone").value("+905550000000"))
                .andExpect(jsonPath("$.addressLine").doesNotExist())
                .andExpect(jsonPath("$.shipment.status").value("DELIVERY_FAILED"))
                .andExpect(jsonPath("$.resolvable").value(true));
        mockMvc.perform(get("/api/v1/admin/seller-orders?status=NEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    @DisplayName("REFUND: ürün + kargo iade, UNDELIVERED nedeni, REFUNDED, stok değişmez, iki tarafa e-posta")
    void iade_karari() throws Exception {
        var sellerOrder = undeliveredSellerOrder();
        mail.reset();

        resolve(sellerOrder, "{\"decision\":\"REFUND\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.resolvable").value(false));

        assertThat(provider.refunds()).singleElement().satisfies(refund -> {
            assertThat(refund.amount()).isEqualTo(2 * 2_450 + 9_900);
            assertThat(refund.subMerchantKey()).contains("test-sub-seller-" + seller);
        });
        assertThat(jdbc.queryForObject("select reason from refunds", String.class)).isEqualTo("UNDELIVERED");
        assertThat(jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, tomato))
                .isEqualTo(8);
        assertThat(jdbc.queryForMap("select to_status, actor_account_id from order_status_changes order by id desc limit 1"))
                .containsEntry("to_status", "REFUNDED")
                .containsEntry("actor_account_id", admin);
        assertThat(mail.sent()).extracting(RecordingMailSender.SentMail::to)
                .containsExactlyInAnyOrder("alici@ornek.test", "uretici@ornek.test");
    }

    @Test
    @DisplayName("CLOSE gerekçe ister (yok veya kısa: 400 reason); gerekçeyle CLOSED, iade yok, gerekçe geçmişte")
    void kapatma_karari() throws Exception {
        var sellerOrder = undeliveredSellerOrder();

        resolve(sellerOrder, "{\"decision\":\"CLOSE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        resolve(sellerOrder, "{\"decision\":\"CLOSE\",\"reason\":\"kısa\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        resolve(sellerOrder, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("decision"));

        resolve(sellerOrder, "{\"decision\":\"CLOSE\",\"reason\":\"Alıcı adreste yoktu ve şubeden almadı.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(provider.refunds()).isEmpty();
        assertThat(jdbc.queryForObject("select reason from order_status_changes order by id desc limit 1", String.class))
                .isEqualTo("Alıcı adreste yoktu ve şubeden almadı.");
    }

    @Test
    @DisplayName("Karar bekliyor olmayan sipariş (karara bağlanmış, kargodaki): 409 ORDER_INVALID_TRANSITION")
    void uygun_olmayan_durum() throws Exception {
        var sellerOrder = undeliveredSellerOrder();
        resolve(sellerOrder, "{\"decision\":\"REFUND\"}").andExpect(status().isOk());

        resolve(sellerOrder, "{\"decision\":\"REFUND\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));
        assertThat(provider.refunds()).hasSize(1);

        jdbc.update("update seller_orders set status = 'SHIPPED' where id = ?", sellerOrder);
        resolve(sellerOrder, "{\"decision\":\"CLOSE\",\"reason\":\"Kargoda olan sipariş için deneme.\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));
    }

    @Test
    @DisplayName("İade başarısız olursa karar gerçekleşmez: 502, durum DELIVERY_FAILED")
    void iade_basarisiz() throws Exception {
        var sellerOrder = undeliveredSellerOrder();
        provider.failRefunds();

        resolve(sellerOrder, "{\"decision\":\"REFUND\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYMENT_PROVIDER_ERROR.name()));
        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("DELIVERY_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from refunds", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Admin olmayan: 403; oturumsuz: 401")
    void yetki() throws Exception {
        var sellerOrder = undeliveredSellerOrder();

        mockMvc.perform(get("/api/v1/admin/seller-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/seller-orders/" + sellerOrder + "/resolution")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REFUND\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/seller-orders/" + sellerOrder)).andExpect(status().isUnauthorized());
        assertThat(provider.refunds()).isEmpty();
    }

    /** Ödenmiş, gönderisi açılmış, kargoya verilmiş ve teslim edilemeyen satıcı siparişi. */
    private long undeliveredSellerOrder() throws Exception {
        fixtures.cartItem(buyer, tomato, 2);
        var number = flows.paidOrder(buyer, buyerToken);
        var sellerOrder = flows.sellerOrderId(number);
        var ref = flows.openShipment(sellerToken, sellerOrder);
        integrator.report(ref, ShipmentStatus.HANDED_OVER);
        poller.pollOnce();
        integrator.report(ref, ShipmentStatus.DELIVERY_FAILED);
        poller.pollOnce();
        assertThat(jdbc.queryForObject("select status from seller_orders where id = ?", String.class, sellerOrder))
                .isEqualTo("DELIVERY_FAILED");
        return sellerOrder;
    }

    private ResultActions resolve(long sellerOrder, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/seller-orders/" + sellerOrder + "/resolution")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
