package com.bahceden.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Satın alınabilirlik ve kargoya verme süresi (storefront-catalog "Satın
 * alınabilirlik", product-listings; add-cart-and-checkout D1, D3).
 *
 * <p>{@code critical}: satın alınabilirlik koşulu sepet ve siparişin de
 * dayandığı tek karardır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Satın alınabilirlik ve kargoya verme süresi")
class PurchasabilityTest {

    private static final String LISTINGS = "/api/v1/sellers/me/products";

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

    private ListingFixtures fixtures;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Yayında, satıcısı onaylı, ödeme hesabı etkin ve stokta: purchasable = true (liste ve detay)")
    void satin_alinabilir() throws Exception {
        var seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        fixtures.publishedProduct(seller, "hazir-domates", 2450, 10);

        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'hazir-domates')].purchasable").value(true));
        mockMvc.perform(get("/api/v1/products/hazir-domates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasable").value(true))
                .andExpect(jsonPath("$.dispatchDays").value(2));
    }

    @Test
    @DisplayName("Ödeme hesabı yok, reddedilmiş veya stok sıfır: ürün vitrinde görünür ama purchasable = false")
    void satin_alinamaz() throws Exception {
        var noAccount = fixtures.approvedSeller("hesapsiz@ornek.test");
        fixtures.publishedProduct(noAccount, "hesapsiz-urun", 1000, 5);
        var rejected = fixtures.approvedSeller("reddedilen@ornek.test");
        fixtures.activePayoutAccount(rejected);
        jdbc.update("update seller_payout_accounts set status = 'PROVIDER_REJECTED' where seller_id = ?",
                fixtures.sellerIdOf(rejected));
        fixtures.publishedProduct(rejected, "reddedilen-urun", 1000, 5);
        var ready = fixtures.approvedSeller("hazir@ornek.test");
        fixtures.activePayoutAccount(ready);
        fixtures.publishedProduct(ready, "tukenen-urun", 1000, 0);

        for (var slug : new String[] {"hesapsiz-urun", "reddedilen-urun", "tukenen-urun"}) {
            mockMvc.perform(get("/api/v1/products").param("size", "50"))
                    .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].purchasable").value(false));
            mockMvc.perform(get("/api/v1/products/" + slug))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.purchasable").value(false));
        }
    }

    @Test
    @DisplayName("Kargoya verme süresi: gönderilmezse yeni ilanda 2; güncellemede verilen değer, verilmezse korunur")
    void kargoya_verme_suresi() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));

        var created = send(post(LISTINGS), token, listing(null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispatchDays").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        send(put(LISTINGS + "/" + id), token, listing(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchDays").value(3));
        send(put(LISTINGS + "/" + id), token, listing(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchDays").value(3));
    }

    @Test
    @DisplayName("Kargoya verme süresi 1–3 dışında: 400 ve dispatchDays alan hatası")
    void kargoya_verme_suresi_aralik_disi() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));

        for (int days : new int[] {0, 4}) {
            send(post(LISTINGS), token, listing(days))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("dispatchDays"));
        }
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
                               String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> listing(Integer dispatchDays) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", "Salkım Domates");
        body.put("unit", "KILOGRAM");
        body.put("priceAmount", 2450);
        body.put("stockQuantity", 40);
        if (dispatchDays != null) {
            body.put("dispatchDays", dispatchDays);
        }
        return body;
    }
}
