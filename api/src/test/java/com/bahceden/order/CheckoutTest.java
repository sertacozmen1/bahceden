package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.payment.PaymentProvider.PaymentOutcome;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.hamcrest.Matchers;
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
 * Sipariş oluşturma ve ödeme (cart-and-checkout spec'i "Sipariş sepetten
 * oluşturulur…", "Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca
 * düşer"; add-cart-and-checkout D6–D8).
 *
 * <p>{@code critical}: para, stok ve sipariş durumu. Ödeme sağlayıcısı test
 * uygulamasıdır; dışarı çıkılmaz (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("/api/v1/orders (sipariş oluşturma ve ödeme)")
class CheckoutTest {

    private static final String URL = "/api/v1/orders";

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
    private long address;
    private long seller;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        mail.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "en", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        address = fixtures.address(buyer, "35");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
        mail.reset();
    }

    @Test
    @DisplayName("Başarılı sipariş: 201, Location, ödeme sayfası; tutarlar satıcı siparişine yazılır, stok düşmez")
    void siparis_olusturma() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 24_550, 10);
        fixtures.cartItem(buyer, tomato, 1);

        var body = place(buyerToken, "anahtar-0001", address)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/v1/orders/BHC-")))
                .andExpect(jsonPath("$.orderNumber").value(Matchers.matchesPattern("BHC-[0-9A-Z]{10}")))
                .andExpect(jsonPath("$.paymentRedirectUrl").value(
                        Matchers.startsWith("http://localhost:3000/checkout/return?order=BHC-")))
                .andReturn().getResponse().getContentAsString();

        var row = jdbc.queryForMap("""
                select so.status, so.items_total, so.shipping_fee, so.commission_rate_bp, so.commission_fixed,
                       so.commission_amount, so.seller_payout, so.dispatch_days, o.grand_total, o.status as order_status,
                       o.ship_province_code
                from seller_orders so join orders o on o.id = so.order_id""");
        assertThat(row).containsEntry("status", "PENDING_PAYMENT")
                .containsEntry("items_total", 24_550L)
                .containsEntry("shipping_fee", 9_900L)
                .containsEntry("commission_rate_bp", 500)
                .containsEntry("commission_fixed", 300L)
                .containsEntry("commission_amount", 1_528L)
                .containsEntry("seller_payout", 23_022L)
                .containsEntry("dispatch_days", 2)
                .containsEntry("grand_total", 34_450L)
                .containsEntry("order_status", "PENDING_PAYMENT")
                .containsEntry("ship_province_code", "35");
        assertThat(stockOf(tomato)).isEqualTo(10);
        assertThat(provider.initiated()).singleElement().satisfies(request -> {
            assertThat(request.amount()).isEqualTo(34_450);
            assertThat(request.shares()).singleElement().satisfies(share -> {
                assertThat(share.amount()).isEqualTo(34_450);
                assertThat(share.sellerPayout()).isEqualTo(23_022);
                assertThat(share.subMerchantKey()).isEqualTo("test-sub-seller-" + seller);
            });
        });
        assertThat(objectMapper.readTree(body).get("orderNumber").asText()).hasSize(14);
    }

    @Test
    @DisplayName("Aynı Idempotency-Key ile tekrar: yeni sipariş açılmaz, ilki döner; anahtar yoksa 400")
    void tekrar_anahtari() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);

        var first = numberOf(place(buyerToken, "anahtar-0002", address).andExpect(status().isCreated()));
        var second = numberOf(place(buyerToken, "anahtar-0002", address).andExpect(status().isCreated()));

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        place(buyerToken, null, address)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("Oturum yoksa 401")
    void oturumsuz() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Boş sepet 422 CART_EMPTY; satın alınamayan veya stoğu aşan satır 422 CART_HAS_UNAVAILABLE_ITEMS")
    void sepet_kosullari() throws Exception {
        place(buyerToken, "anahtar-0003", address)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CART_EMPTY.name()));

        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, product, 5);
        jdbc.update("update products set stock_quantity = 4 where id = ?", product);
        place(buyerToken, "anahtar-0004", address)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CART_HAS_UNAVAILABLE_ITEMS.name()));

        jdbc.update("update products set stock_quantity = 10, status = 'ARCHIVED' where id = ?", product);
        place(buyerToken, "anahtar-0005", address)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CART_HAS_UNAVAILABLE_ITEMS.name()));
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Teslimat yapılamayan adres 422 ADDRESS_NOT_DELIVERABLE; başkasının adresi 404")
    void adres_kosullari() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        long bursa = fixtures.address(buyer, "16");
        var other = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");
        long othersAddress = fixtures.address(other, "35");

        place(buyerToken, "anahtar-0006", bursa)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ADDRESS_NOT_DELIVERABLE.name()));
        place(buyerToken, "anahtar-0007", othersAddress)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ADDRESS_NOT_FOUND.name()));
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sağlayıcıya ulaşılamazsa 502; sipariş açılmaz")
    void saglayici_ulasilamaz() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        provider.makeUnavailable();

        place(buyerToken, "anahtar-0008", address)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYMENT_PROVIDER_ERROR.name()));
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Başarılı ödeme: stok düşer, PAID ve NEW, son tarih yazılır, satın alınan satır sepetten çıkar, iki dilde e-posta")
    void basarili_odeme() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomato, 3);
        var number = numberOf(place(buyerToken, "anahtar-0009", address));
        jdbc.update("update products set price_amount = 9999 where id = ?", tomato);

        complete(buyerToken, number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.sellerOrders[0].status").value("NEW"))
                .andExpect(jsonPath("$.sellerOrders[0].dispatchDueAt").isNotEmpty())
                .andExpect(jsonPath("$.sellerOrders[0].cancellable").value(true))
                .andExpect(jsonPath("$.sellerOrders[0].items[0].unitPrice.amount").value(2_450))
                .andExpect(jsonPath("$.shippingAddress.provinceName").value("İzmir"));

        assertThat(stockOf(tomato)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select to_status from order_status_changes order by id", String.class))
                .containsExactly("PENDING_PAYMENT", "NEW");
        assertThat(mail.sent()).hasSize(2);
        assertThat(mail.sent()).anySatisfy(sent -> {
            assertThat(sent.to()).isEqualTo("alici@ornek.test");
            assertThat(sent.subject()).contains("we received your order", number);
        });
        assertThat(mail.sent()).anySatisfy(sent -> {
            assertThat(sent.to()).isEqualTo("uretici@ornek.test");
            assertThat(sent.subject()).contains("yeni sipariş", number);
        });

        complete(buyerToken, number).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(stockOf(tomato)).isEqualTo(7);
    }

    @Test
    @DisplayName("Son ürünü iki alıcı aynı anda öder: biri PAID, diğeri tam iade ve CANCELLED_OUT_OF_STOCK; stok negatife düşmez")
    void son_urun_esszamanli() throws Exception {
        long last = fixtures.publishedProduct(seller, "son-urun", 5_000, 1);
        var second = fixtures.account("ikinci@ornek.test", "tr", "ROLE_CUSTOMER");
        var secondToken = fixtures.token(second, "ROLE_CUSTOMER");
        long secondAddress = fixtures.address(second, "34");
        fixtures.cartItem(buyer, last, 1);
        fixtures.cartItem(second, last, 1);
        var firstNumber = numberOf(place(buyerToken, "anahtar-0010", address));
        var secondNumber = numberOf(place(secondToken, "anahtar-0011", secondAddress));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<Callable<Void>>();
            tasks.add(() -> {
                complete(buyerToken, firstNumber).andExpect(status().isOk());
                return null;
            });
            tasks.add(() -> {
                complete(secondToken, secondNumber).andExpect(status().isOk());
                return null;
            });
            for (var future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        assertThat(jdbc.queryForList("select status from orders order by status", String.class))
                .containsExactly("CANCELLED_OUT_OF_STOCK", "PAID");
        assertThat(stockOf(last)).isZero();
        assertThat(provider.refunds()).singleElement().satisfies(refund -> {
            assertThat(refund.amount()).isEqualTo(5_000 + 9_900);
            assertThat(refund.subMerchantKey()).isEmpty();
        });
        assertThat(jdbc.queryForObject("select reason from refunds", String.class)).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("Başarısız ödeme: PAYMENT_FAILED, satıcı siparişleri VOID; stok ve sepet değişmez")
    void basarisiz_odeme() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomato, 2);
        var number = numberOf(place(buyerToken, "anahtar-0012", address));
        provider.answerVerifyWith(PaymentOutcome.FAILED);

        complete(buyerToken, number)
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.sellerOrders[0].status").value("VOID"))
                .andExpect(jsonPath("$.sellerOrders[0].cancellable").value(false));

        assertThat(stockOf(tomato)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isEqualTo(1);
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Ödeme 30 dakika içinde tamamlanmazsa PAYMENT_EXPIRED; alınmış para iade edilir, stok değişmez")
    void suresi_dolan_odeme() throws Exception {
        long tomato = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        fixtures.cartItem(buyer, tomato, 2);
        var number = numberOf(place(buyerToken, "anahtar-0013", address));
        clock.advance(Duration.ofMinutes(31));

        complete(fixtures.token(buyer, "ROLE_CUSTOMER"), number)
                .andExpect(jsonPath("$.status").value("PAYMENT_EXPIRED"));

        assertThat(stockOf(tomato)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select reason from refunds", String.class)).isEqualTo("PAYMENT_EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ödeme henüz tamamlanmadıysa sipariş bekler; başkasının siparişi 404")
    void bekleyen_ve_baskasinin_siparisi() throws Exception {
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        var number = numberOf(place(buyerToken, "anahtar-0014", address));
        provider.answerVerifyWith(PaymentOutcome.PENDING);

        complete(buyerToken, number).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        var other = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");
        complete(fixtures.token(other, "ROLE_CUSTOMER"), number)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ORDER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("İki satıcılı sepet: iki satıcı siparişi, eşik kuralı satıcı başına, tek ödeme ve iki pay")
    void iki_satici() throws Exception {
        var other = fixtures.approvedSeller("ikinci@ornek.test");
        fixtures.activePayoutAccount(other);
        fixtures.cartItem(buyer, fixtures.publishedProduct(seller, "domates", 2_450, 10), 1);
        fixtures.cartItem(buyer, fixtures.publishedProduct(other, "kiraz", 50_000, 10), 2);

        place(buyerToken, "anahtar-0015", address).andExpect(status().isCreated());

        assertThat(jdbc.queryForList("select shipping_fee from seller_orders order by id", Long.class))
                .containsExactly(9_900L, 0L);
        assertThat(jdbc.queryForObject("select grand_total from orders", Long.class)).isEqualTo(2_450 + 9_900 + 100_000);
        assertThat(provider.initiated().getFirst().shares()).hasSize(2);
    }

    private ResultActions place(String token, String idempotencyKey, long addressId) throws Exception {
        var request = post(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + "}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private ResultActions complete(String token, String orderNumber) throws Exception {
        return mockMvc.perform(post(URL + "/" + orderNumber + "/payment").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String numberOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("orderNumber").asText();
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, productId);
    }
}
