package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.payment.PaymentProvider.PaymentOutcome;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ödeme dönüşü gelmeyen siparişlerin işi (cart-and-checkout spec'i "Ödeme
 * sağlayıcıdan doğrulanır…", add-stale-record-cleanup D1).
 *
 * <p>{@code critical}: para ve sipariş durumu. Zamanlayıcı testlerde kapalıdır;
 * iş sabit saatle doğrudan çağrılır. Ödeme süresi 30 dakika, tolerans 1 saattir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Ödenmemiş sipariş işi")
class PendingPaymentSweepTest {

    private static final AtomicInteger KEYS = new AtomicInteger();

    /** Ödeme süresi (30 dk) + tolerans (1 sa) geçmiş olsun. */
    private static final Duration PAST_GRACE = Duration.ofMinutes(91);

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

    @Autowired
    private PendingPaymentSweep sweep;

    @Autowired
    private ApplicationContext context;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;
    private long address;
    private long seller;
    private long product;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        address = fixtures.address(buyer, "35");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        product = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, product, 2);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Sağlayıcı beklemede: süresi dolmuş, satıcı siparişleri VOID, geçmişte yapan sistem; stok, sepet ve para değişmez")
    void beklemedeki_odeme_suresi_dolmus_olur() throws Exception {
        var number = placedOrder();
        provider.answerVerifyWith(PaymentOutcome.PENDING);
        clock.advance(PAST_GRACE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(orderStatus(number)).isEqualTo("PAYMENT_EXPIRED");
        assertThat(jdbc.queryForObject("select status from seller_orders", String.class)).isEqualTo("VOID");
        assertThat(jdbc.queryForMap("""
                select to_status, actor_account_id from order_status_changes order by id desc limit 1"""))
                .containsEntry("to_status", "VOID")
                .containsEntry("actor_account_id", null);
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isEqualTo(1);
        assertThat(provider.refunds()).isEmpty();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Sağlayıcı başarısız: ödeme başarısız")
    void basarisiz_odeme() throws Exception {
        var number = placedOrder();
        provider.answerVerifyWith(PaymentOutcome.FAILED);
        clock.advance(PAST_GRACE);

        sweep.sweepOnce();

        assertThat(orderStatus(number)).isEqualTo("PAYMENT_FAILED");
        assertThat(provider.refunds()).isEmpty();
    }

    @Test
    @DisplayName("Geç alınmış ödeme: tamamı PAYMENT_EXPIRED nedeniyle iade edilir, sipariş süresi dolmuş; stok düşmez")
    void gec_alinmis_odeme_iade_edilir() throws Exception {
        var number = placedOrder();
        provider.answerVerifyWith(PaymentOutcome.SUCCEEDED);
        clock.advance(PAST_GRACE);

        sweep.sweepOnce();

        assertThat(orderStatus(number)).isEqualTo("PAYMENT_EXPIRED");
        var grandTotal = jdbc.queryForObject("select grand_total from orders", Long.class);
        assertThat(provider.refunds()).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(grandTotal));
        assertThat(jdbc.queryForObject("select reason from refunds", String.class)).isEqualTo("PAYMENT_EXPIRED");
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("Tolerans dolmamış ve ödenmiş siparişe dokunulmaz")
    void tolerans_ve_odenmis_siparis() throws Exception {
        var paid = placedOrder();
        complete(paid).andExpect(jsonPath("$.status").value("PAID"));
        fixtures.cartItem(buyer, product, 1);
        var waiting = placedOrder();
        provider.answerVerifyWith(PaymentOutcome.PENDING);
        clock.advance(Duration.ofMinutes(45));

        assertThat(sweep.sweepOnce()).isZero();

        assertThat(orderStatus(paid)).isEqualTo("PAID");
        assertThat(orderStatus(waiting)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("Bir siparişte sağlayıcı hata verirse o değişmez, diğeri kapanır; sonraki turda yeniden denenir")
    void saglayici_hatasi_yalitilir() throws Exception {
        var first = placedOrder();
        // Ödenmemiş sipariş sepeti boşaltmaz; ikinci sipariş aynı sepetten açılır.
        var second = placedOrder();
        provider.failVerifyOf(paymentReference(first));
        provider.answerVerifyWith(PaymentOutcome.PENDING);
        clock.advance(PAST_GRACE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(orderStatus(first)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(second)).isEqualTo("PAYMENT_EXPIRED");
        provider.reset();
        provider.answerVerifyWith(PaymentOutcome.PENDING);
        assertThat(sweep.sweepOnce()).isEqualTo(1);
        assertThat(orderStatus(first)).isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    @DisplayName("Sağlayıcı hatası veren en eski siparişler arkadakini bekletmez: sayfa boyutu 2, üçüncü sipariş aynı turda kapanır; her sipariş bir kez denenir (ADR-100)")
    void hatali_siparisler_arkadakini_bekletmez() throws Exception {
        var first = placedOrder();
        var second = placedOrder();
        var third = placedOrder();
        // Test sayfa boyutu 2'dir: hata veren iki sipariş ilk sayfayı doldurur.
        provider.failVerifyOf(paymentReference(first));
        provider.failVerifyOf(paymentReference(second));
        provider.answerVerifyWith(PaymentOutcome.PENDING);
        clock.advance(PAST_GRACE);

        assertThat(sweep.sweepOnce()).isEqualTo(1);

        assertThat(orderStatus(first)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(second)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(third)).isEqualTo("PAYMENT_EXPIRED");
        assertThat(provider.verifyCallsOf(paymentReference(first))).isEqualTo(1);
        assertThat(provider.verifyCallsOf(paymentReference(second))).isEqualTo(1);
    }

    @Test
    @DisplayName("İşten sonra ödeme dönüşü sonuçlanmış siparişi döner; çift iade yok")
    void isten_sonra_odeme_donusu() throws Exception {
        var number = placedOrder();
        provider.answerVerifyWith(PaymentOutcome.SUCCEEDED);
        clock.advance(PAST_GRACE);
        sweep.sweepOnce();
        // Saat ileri alındı; 15 dakikalık erişim token'ı yenilenir (ADR-008).
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");

        complete(number).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAYMENT_EXPIRED"));

        assertThat(provider.refunds()).hasSize(1);
    }

    @Test
    @DisplayName("Testlerde zamanlayıcı kurulmaz (cleanup.enabled=false)")
    void zamanlayici_kapali() {
        assertThat(context.getBeanNamesForType(PendingPaymentSweepSchedule.class)).isEmpty();
    }

    private String placedOrder() throws Exception {
        var body = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", "temizlik-anahtari-" + KEYS.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + address + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("orderNumber").asText();
    }

    private ResultActions complete(String number) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + number + "/payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken));
    }

    private String orderStatus(String number) {
        return jdbc.queryForObject("select status from orders where order_number = ?", String.class, number);
    }

    private String paymentReference(String number) {
        return jdbc.queryForObject("""
                select p.provider_reference from payments p join orders o on o.id = p.order_id
                where o.order_number = ?""", String.class, number);
    }

    private int stock() {
        return jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, product);
    }
}
