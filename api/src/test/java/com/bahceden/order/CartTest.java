package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
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
 * Sepet (cart-and-checkout spec'i "Sepet hesaba bağlıdır ve satıcılara göre
 * gruplanır", "Kargo ücreti satıcı başınadır"; add-cart-and-checkout D4, D7).
 *
 * <p>{@code critical}: satın alınabilirlik, stok sınırı ve kargo ücreti para ve
 * stoğa dokunan kurallardır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("/api/v1/cart (sepet)")
class CartTest {

    private static final String URL = "/api/v1/cart";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;
    private long seller;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Oturum olmadan sepet okunamaz ve değiştirilemez: 401")
    void oturumsuz() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);

        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(URL + "/items/" + product).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Boş sepet: grup yok, toplamlar sıfır")
    void bos_sepet() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.grandTotal.amount").value(0));
    }

    @Test
    @DisplayName("Ekleme ve adet değiştirme: satır tutarı, kargo 99 TL, ücretsiz kargoya kalan, genel toplam")
    void ekleme_ve_adet_degistirme() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);

        setItem(product, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].seller.slug").value("uretici"))
                .andExpect(jsonPath("$.groups[0].lines[0].lineTotal.amount").value(4_900))
                .andExpect(jsonPath("$.groups[0].lines[0].available").value(true))
                .andExpect(jsonPath("$.groups[0].shippingFee.amount").value(9_900))
                .andExpect(jsonPath("$.groups[0].freeShippingRemaining.amount").value(95_100))
                .andExpect(jsonPath("$.groups[0].dispatchDays").value(2))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.grandTotal.amount").value(4_900 + 9_900));

        setItem(product, 3).andExpect(jsonPath("$.groups[0].lines[0].quantity").value(3));
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Eşik: 999,99 TL'de kargo 99 TL ve 0,01 TL kaldı; tam 1000 TL'de kargo ücretsiz; iki satıcı ayrı hesaplanır")
    void kargo_esigi_ve_iki_satici() throws Exception {
        long below = fixtures.publishedProduct(seller, "esik-alti", 99_999, 5);
        var other = fixtures.approvedSeller("ikinci@ornek.test");
        fixtures.activePayoutAccount(other);
        long exact = fixtures.publishedProduct(other, "esikte", 50_000, 5);

        setItem(below, 1).andExpect(status().isOk());
        setItem(exact, 2)
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[0].shippingFee.amount").value(9_900))
                .andExpect(jsonPath("$.groups[0].freeShippingRemaining.amount").value(1))
                .andExpect(jsonPath("$.groups[1].itemsTotal.amount").value(100_000))
                .andExpect(jsonPath("$.groups[1].shippingFee.amount").value(0))
                .andExpect(jsonPath("$.groups[1].freeShippingRemaining.amount").value(0))
                .andExpect(jsonPath("$.shippingTotal.amount").value(9_900))
                .andExpect(jsonPath("$.grandTotal.amount").value(99_999 + 100_000 + 9_900));
    }

    @Test
    @DisplayName("Adet stoğu aşarsa veya 1–99 dışındaysa 400, quantity alanı")
    void adet_siniri() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 3);

        for (int quantity : new int[] {4, 0, 100}) {
            setItem(product, quantity)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        }
        assertThat(jdbc.queryForObject("select count(*) from cart_items", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Satın alınamayan ürün (ödeme hesabı yok, tükendi, taslak) eklenemez: 422 PRODUCT_NOT_PURCHASABLE")
    void satin_alinamayan_urun() throws Exception {
        var noAccount = fixtures.approvedSeller("hesapsiz@ornek.test");
        long withoutAccount = fixtures.publishedProduct(noAccount, "hesapsiz", 1_000, 5);
        long soldOut = fixtures.publishedProduct(seller, "tukenen", 1_000, 0);
        long draft = fixtures.publishedProduct(seller, "taslak", 1_000, 5);
        jdbc.update("update products set status = 'DRAFT' where id = ?", draft);

        for (long product : new long[] {withoutAccount, soldOut, draft}) {
            setItem(product, 1)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_NOT_PURCHASABLE.name()));
        }
        setItem(999_999, 1).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Satıcı kendi ürününü ekleyemez: 422 CANNOT_BUY_OWN_PRODUCT")
    void kendi_urunu() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);

        mockMvc.perform(put(URL + "/items/" + product)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CANNOT_BUY_OWN_PRODUCT.name()));
    }

    @Test
    @DisplayName("50 farklı ürün sınırı: 51. ürün 422 CART_FULL; var olan satırın adedi yine değişir")
    void sepet_siniri() throws Exception {
        long first = fixtures.publishedProduct(seller, "urun-0", 1_000, 5);
        setItem(first, 1).andExpect(status().isOk());
        var cartId = jdbc.queryForObject("select id from carts", Long.class);
        for (int i = 1; i < CartService.MAX_LINES; i++) {
            long product = fixtures.publishedProduct(seller, "urun-" + i, 1_000, 5);
            jdbc.update("insert into cart_items (cart_id, product_id, quantity) values (?, ?, 1)", cartId, product);
        }
        long extra = fixtures.publishedProduct(seller, "fazla", 1_000, 5);

        setItem(extra, 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.CART_FULL.name()));
        setItem(first, 2).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sepetteki ürün yayından çekilir veya satıcısı askıya alınır: satır kalır, available=false, toplama girmez")
    void satin_alinamaz_hale_gelen_satir() throws Exception {
        long kept = fixtures.publishedProduct(seller, "kalan", 1_000, 5);
        long unpublished = fixtures.publishedProduct(seller, "cekilen", 2_000, 5);
        var other = fixtures.approvedSeller("askiya@ornek.test");
        fixtures.activePayoutAccount(other);
        long suspendedSellers = fixtures.publishedProduct(other, "askidaki", 3_000, 5);
        setItem(kept, 1);
        setItem(unpublished, 1);
        setItem(suspendedSellers, 1);

        jdbc.update("update products set status = 'DRAFT' where id = ?", unpublished);
        jdbc.update("update sellers set status = 'SUSPENDED' where id = ?", fixtures.sellerIdOf(other));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.groups[0].lines[1].available").value(false))
                .andExpect(jsonPath("$.groups[0].itemsTotal.amount").value(1_000))
                .andExpect(jsonPath("$.groups[1].lines[0].available").value(false))
                .andExpect(jsonPath("$.groups[1].shippingFee.amount").value(0))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.hasUnavailableItems").value(true))
                .andExpect(jsonPath("$.grandTotal.amount").value(1_000 + 9_900));
    }

    @Test
    @DisplayName("Fiyat değişirse sepet bir sonraki okumada yeni fiyatla döner")
    void guncel_fiyat() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        setItem(product, 2);

        jdbc.update("update products set price_amount = 3000 where id = ?", product);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.groups[0].lines[0].unitPrice.amount").value(3_000))
                .andExpect(jsonPath("$.itemsTotal.amount").value(6_000));
    }

    @Test
    @DisplayName("Satır silinir; olmayan satırı silmek sepeti değiştirmez")
    void silme() throws Exception {
        long product = fixtures.publishedProduct(seller, "domates", 2_450, 10);
        setItem(product, 1);

        mockMvc.perform(delete(URL + "/items/" + product).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty());
        mockMvc.perform(delete(URL + "/items/" + product).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sepet okuması satır sayısından bağımsız sabit sayıda sorgu (≤ 3)")
    void sorgu_sayisi() throws Exception {
        for (int i = 0; i < 6; i++) {
            var owner = fixtures.approvedSeller("satici" + i + "@ornek.test");
            fixtures.activePayoutAccount(owner);
            setItem(fixtures.publishedProduct(owner, "coklu-" + i, 1_000, 5), 1);
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.groups.length()").value(6));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    private ResultActions setItem(long productId, int quantity) throws Exception {
        return mockMvc.perform(put(URL + "/items/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"));
    }
}
