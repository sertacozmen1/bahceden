package com.bahceden.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.shipping.domain.ShipmentStatus;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.ScriptedShippingIntegrator;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
 * Gönderi açma, etiket ve gönderisi açık siparişin iptali (shipments spec'i
 * "Satıcı kargoya hazır siparişi için gönderi açar", "Etiket yalnızca
 * satıcısına korumalı olarak sunulur"; orders spec'i iptal; add-shipments D3,
 * D4, D6; change-cancel-rejection-syncs-shipment D1).
 *
 * <p>{@code critical}: sipariş durumu, sahiplik ve iade.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class, ScriptedShippingIntegrator.Config.class})
@DisplayName("Gönderi açma, etiket ve iptal")
class ShipmentTest {

    private static final String MEDIUM = "{\"packageSize\":\"MEDIUM\"}";

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
        integrator.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Gönderi açılır: 201, CREATED; sipariş NEW kalır; entegratöre gönderici ve alıcı bilgisi, desi gider")
    void gonderi_acilir() throws Exception {
        var sellerOrder = paidSellerOrder();

        flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carrier").value("Test Kargo"))
                .andExpect(jsonPath("$.trackingNumber").value("TK00000001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.packageSize").value("MEDIUM"));

        assertThat(jdbc.queryForObject("select status from seller_orders where id = ?", String.class, sellerOrder))
                .isEqualTo("NEW");
        assertThat(integrator.created()).singleElement().satisfies(request -> {
            assertThat(request.reference()).isEqualTo("BHC-" + sellerOrder + "-1");
            assertThat(request.sender().name()).isEqualTo("Satıcı uretici");
            assertThat(request.sender().phone()).isEqualTo("+905550000000");
            assertThat(request.recipient().name()).isEqualTo("Örnek Alıcı");
            assertThat(request.recipient().provinceName()).isEqualTo("İzmir");
            assertThat(request.desi()).isEqualTo(15);
        });
        mockMvc.perform(get("/api/v1/sellers/me/orders/" + sellerOrder)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.shipment.trackingNumber").value("TK00000001"))
                .andExpect(jsonPath("$.cancellable").value(true));
    }

    @Test
    @DisplayName("Tekrarlanan istek yeni gönderi açmaz: 200 ve aynı takip numarası")
    void tekrarlanan_istek() throws Exception {
        var sellerOrder = paidSellerOrder();
        flows.createShipment(sellerToken, sellerOrder, MEDIUM).andExpect(status().isCreated());

        flows.createShipment(sellerToken, sellerOrder, "{\"packageSize\":\"LARGE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TK00000001"))
                .andExpect(jsonPath("$.packageSize").value("MEDIUM"));
        assertThat(integrator.created()).hasSize(1);
    }

    @Test
    @DisplayName("Eşzamanlı iki istek tek gönderi açar")
    void eszamanli_istek() throws Exception {
        var sellerOrder = paidSellerOrder();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                        .andReturn().getResponse().getStatus());
            }
            var statuses = new ArrayList<Integer>();
            for (var future : executor.invokeAll(calls)) {
                statuses.add(future.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        } finally {
            executor.shutdown();
        }
        assertThat(integrator.created()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from shipments", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Koli boyutu yok veya tanımsız: 400 packageSize")
    void gecersiz_koli_boyutu() throws Exception {
        var sellerOrder = paidSellerOrder();

        flows.createShipment(sellerToken, sellerOrder, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("packageSize"));
        flows.createShipment(sellerToken, sellerOrder, "{\"packageSize\":\"HUGE\"}")
                .andExpect(status().isBadRequest());
        assertThat(integrator.created()).isEmpty();
    }

    @Test
    @DisplayName("Başka satıcı: 404; onaysız satıcı: 403; oturumsuz: 401")
    void yetki() throws Exception {
        var sellerOrder = paidSellerOrder();
        var other = fixtures.approvedSeller("ikinci@ornek.test");
        var suspended = fixtures.account("askida@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_SELLER");
        fixtures.seller(suspended, "askida", "SUSPENDED");

        flows.createShipment(fixtures.sellerToken(other), sellerOrder, MEDIUM)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_ORDER_NOT_FOUND.name()));
        flows.createShipment(fixtures.sellerToken(suspended), sellerOrder, MEDIUM)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_APPROVED.name()));
        flows.createShipment(fixtures.token(buyer, "ROLE_CUSTOMER"), sellerOrder, MEDIUM)
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON).content(MEDIUM))
                .andExpect(status().isUnauthorized());
        assertThat(integrator.created()).isEmpty();
    }

    @Test
    @DisplayName("İptal edilmiş satıcı siparişine gönderi açılmaz: 409 ORDER_INVALID_TRANSITION")
    void uygun_olmayan_durum() throws Exception {
        var sellerOrder = paidSellerOrder();
        jdbc.update("update seller_orders set status = 'CANCELLED' where id = ?", sellerOrder);

        flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));
        assertThat(integrator.created()).isEmpty();
    }

    @Test
    @DisplayName("Satıcının telefonu yoksa: 422 SHIPMENT_SENDER_PHONE_MISSING ve entegratör çağrılmaz")
    void telefon_yok() throws Exception {
        var sellerOrder = paidSellerOrder();
        jdbc.update("update sellers set phone = null where account_id = ?", seller);

        flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SHIPMENT_SENDER_PHONE_MISSING.name()));
        assertThat(integrator.created()).isEmpty();
    }

    @Test
    @DisplayName("Entegratör hatası veya desteklenmeyen etiket türü: 502, gönderi kaydedilmez")
    void entegrator_hatasi() throws Exception {
        var sellerOrder = paidSellerOrder();
        integrator.answerLabelWith("<html>etiket değil</html>".getBytes(StandardCharsets.UTF_8));

        flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR.name()));

        integrator.reset();
        integrator.makeUnavailable();
        flows.createShipment(sellerToken, sellerOrder, MEDIUM)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR.name()));
        assertThat(jdbc.queryForObject("select count(*) from shipments", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Etiket yalnızca sahibine, korumalı başlıklarla; başkasına 404, oturumsuz 401")
    void etiket() throws Exception {
        var sellerOrder = paidSellerOrder();
        flows.openShipment(sellerToken, sellerOrder);
        var url = "/api/v1/sellers/me/orders/" + sellerOrder + "/shipment/label";

        var body = mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(ScriptedShippingIntegrator.PDF_LABEL);

        var other = fixtures.approvedSeller("ikinci@ornek.test");
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_ORDER_NOT_FOUND.name()));
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Gönderisi açık sipariş iptal edilir: gönderi entegratörde ve kayıtta CANCELLED, tam iade")
    void gonderisi_acik_siparis_iptal() throws Exception {
        var number = paidOrderNumber();
        var sellerOrder = flows.sellerOrderId(number);
        var ref = flows.openShipment(sellerToken, sellerOrder);

        buyerCancel(number, sellerOrder).andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrders[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.sellerOrders[0].shipment").doesNotExist());

        assertThat(integrator.cancelled()).containsExactly(ref);
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CANCELLED");
        assertThat(provider.refunds()).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(2_450 + 9_900));
    }

    @Test
    @DisplayName("Entegratör iptali reddederse: 502, sipariş NEW, gönderi CREATED, iade yok")
    void entegrator_iptali_reddeder() throws Exception {
        var number = paidOrderNumber();
        var sellerOrder = flows.sellerOrderId(number);
        flows.openShipment(sellerToken, sellerOrder);
        integrator.refuseCancels();

        buyerCancel(number, sellerOrder)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR.name()));

        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CREATED");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("Paket durum sorgusundan önce kargoya verilmişse alıcı iptali: 409 ORDER_ALREADY_SHIPPED; "
            + "gönderi ve sipariş kargoda, alıcıya kargo e-postası, iade ve stok değişikliği yok")
    void kargoya_verilmis_paketin_iptali_durumu_esitler() throws Exception {
        var number = paidOrderNumber();
        var sellerOrder = flows.sellerOrderId(number);
        var ref = flows.openShipment(sellerToken, sellerOrder);
        var stock = jdbc.queryForObject("select stock_quantity from products", Integer.class);
        integrator.report(ref, ShipmentStatus.HANDED_OVER);
        mail.reset();

        buyerCancel(number, sellerOrder)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_ALREADY_SHIPPED.name()))
                .andExpect(jsonPath("$.message").value(
                        "Paket kargo firmasına verildi; bu sipariş artık iptal edilemez. "
                                + "Teslim aldıktan sonra sorun bildirebilirsiniz."));

        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("SHIPPED");
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("HANDED_OVER");
        assertThat(jdbc.queryForObject("select stock_quantity from products", Integer.class)).isEqualTo(stock);
        assertThat(integrator.cancelled()).isEmpty();
        assertThat(provider.refunds()).isEmpty();
        assertThat(mail.onlySentTo("alici@ornek.test").subject()).contains("kargoya verildi");
    }

    @Test
    @DisplayName("Paket kargoya verilmişse satıcı iptali de 409 ORDER_ALREADY_SHIPPED ve sipariş kargoda")
    void kargoya_verilmis_paketin_satici_iptali() throws Exception {
        var sellerOrder = paidSellerOrder();
        var ref = flows.openShipment(sellerToken, sellerOrder);
        integrator.report(ref, ShipmentStatus.IN_TRANSIT);

        mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrder + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ürün elimde kalmadı, üzgünüm\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_ALREADY_SHIPPED.name()));

        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("SHIPPED");
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("IN_TRANSIT");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("İptal reddedilir ve durum da okunamazsa: 502, sipariş NEW, gönderi CREATED, iade yok")
    void iptal_reddi_ve_durum_okunamaz() throws Exception {
        var number = paidOrderNumber();
        var sellerOrder = flows.sellerOrderId(number);
        var ref = flows.openShipment(sellerToken, sellerOrder);
        integrator.refuseCancels();
        integrator.failStatusOf(ref);

        buyerCancel(number, sellerOrder)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR.name()));

        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("select status from shipments", String.class)).isEqualTo("CREATED");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("Kargoya verilmiş sipariş iptal edilemez: 409 ORDER_INVALID_TRANSITION")
    void kargodaki_siparis_iptal_edilemez() throws Exception {
        var number = paidOrderNumber();
        var sellerOrder = flows.sellerOrderId(number);
        flows.openShipment(sellerToken, sellerOrder);
        jdbc.update("update seller_orders set status = 'SHIPPED' where id = ?", sellerOrder);

        buyerCancel(number, sellerOrder)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_INVALID_TRANSITION.name()));
        assertThat(integrator.cancelled()).isEmpty();
    }

    private String paidOrderNumber() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        return flows.paidOrder(buyer, buyerToken);
    }

    private long paidSellerOrder() throws Exception {
        return flows.sellerOrderId(paidOrderNumber());
    }

    private ResultActions buyerCancel(String number, long sellerOrder) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + number + "/seller-orders/" + sellerOrder + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}
