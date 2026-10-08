package com.bahceden.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
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
 * Satıcının taslak ilanları (product-listings spec'i "Onaylı satıcı ilan
 * oluşturur ve düzenler"; add-product-listings D1, D2).
 *
 * <p>{@code critical}: onaylı satıcı kapısı, sorgu seviyesinde sahiplik ve
 * doğrulama burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("/api/v1/sellers/me/products (satıcı ilanları)")
class SellerListingTest {

    private static final String URL = "/api/v1/sellers/me/products";

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
    private EntityManagerFactory entityManagerFactory;

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
    @DisplayName("Onaylı satıcı taslak oluşturur: 201, DRAFT, Location, geçmiş; vitrinde görünmez")
    void taslak_olusturma() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));

        var id = idOf(create(token, listing("Salkım Domates"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("salkim-domates"))
                .andExpect(jsonPath("$.unitPrice.amount").value(2450))
                .andExpect(jsonPath("$.unitPrice.currency").value("TRY"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist()));

        assertThat(jdbc.queryForList("select from_status, to_status from product_status_changes where product_id = ?", id))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("from_status")).isNull();
                    assertThat(row.get("to_status")).isEqualTo("DRAFT");
                });
        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'salkim-domates')]").isEmpty());
    }

    @Test
    @DisplayName("Satıcı rolü olmayan hesap 403 AUTH_FORBIDDEN; oturum yok 401")
    void rolsuz_ve_oturumsuz() throws Exception {
        var customer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");

        create(fixtures.token(customer, "ROLE_CUSTOMER"), listing("Domates"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_FORBIDDEN.name()));
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kaydı olmayan, bekleyen ve askıdaki satıcı: rol token'da olsa bile 403 SELLER_NOT_APPROVED (ADR-044)")
    void onayli_olmayan_satici_403() throws Exception {
        var noRecord = fixtures.account("kayitsiz@ornek.test", "tr", "ROLE_CUSTOMER");
        var pending = fixtures.account("bekleyen@ornek.test", "tr", "ROLE_CUSTOMER");
        fixtures.seller(pending, "bekleyen", "PENDING");
        var suspended = fixtures.account("askida@ornek.test", "tr", "ROLE_CUSTOMER");
        fixtures.seller(suspended, "askida", "SUSPENDED");

        for (var accountId : new long[] {noRecord, pending, suspended}) {
            var token = fixtures.sellerToken(accountId);
            create(token, listing("Domates"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_APPROVED.name()));
            mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from products where name = 'Domates'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Başkasının ilanı: görme ve düzenleme 404, ilan değişmez")
    void baskasinin_ilani_404() throws Exception {
        var ownerToken = fixtures.sellerToken(fixtures.approvedSeller("sahip@ornek.test"));
        var id = idOf(create(ownerToken, listing("Kuru İncir")).andExpect(status().isCreated()));
        var otherToken = fixtures.sellerToken(fixtures.approvedSeller("baska@ornek.test"));

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_NOT_FOUND.name()));
        update(otherToken, id, listing("Ele Geçirilmiş")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select name from products where id = ?", String.class, id)).isEqualTo("Kuru İncir");
    }

    @Test
    @DisplayName("Kurala uymayan alanlar alan bazlı 400; bozuk JSON 400")
    void alan_dogrulamalari() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        var invalid = new LinkedHashMap<String, Object>();
        invalid.put("name", "");
        invalid.put("unit", "TON");
        invalid.put("priceAmount", 0);
        invalid.put("stockQuantity", -1);

        create(token, invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems(
                        "name", "unit", "priceAmount", "stockQuantity")));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()));
    }

    @Test
    @DisplayName("Kalıcı ad: çakışmada sayı eklenir, ad değişince sabit kalır")
    void slug_uretimi_ve_sabitligi() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        var first = idOf(create(token, listing("Salkım Domates")).andExpect(status().isCreated()));
        create(token, listing("Salkım Domates"))
                .andExpect(jsonPath("$.slug").value("salkim-domates-2"));

        update(token, first, listing("Cherry Domates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cherry Domates"))
                .andExpect(jsonPath("$.slug").value("salkim-domates"));
    }

    @Test
    @DisplayName("Yayındaki ilanın fiyatı değişir; ilan yayında kalır ve vitrin yeni fiyatı gösterir")
    void yayindaki_ilani_duzenleme() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        var id = idOf(create(token, listing("Maydanoz Demeti")).andExpect(status().isCreated()));
        jdbc.update("update products set status = 'PUBLISHED', published_at = now() where id = ?", id);

        var changed = listing("Maydanoz Demeti");
        changed.put("priceAmount", 1990);
        update(token, id, changed)
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.unitPrice.amount").value(1990));

        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'maydanoz-demeti')].unitPrice.amount").value(
                        org.hamcrest.Matchers.contains(1990)));
    }

    @Test
    @DisplayName("Liste: yalnızca kendi ilanları, en son güncellenen üstte, sorgu sayısı sabit")
    void kendi_ilanlarim_listesi() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        for (int i = 1; i <= 5; i++) {
            create(token, listing("Ürün " + i)).andExpect(status().isCreated());
        }
        create(fixtures.sellerToken(fixtures.approvedSeller("baska@ornek.test")), listing("Başkasının"))
                .andExpect(status().isCreated());

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.items[0].name").value("Ürün 5"));
        // Satıcı kapısı + içerik (+ sayım); ilan sayısından bağımsız.
        assertThat(statistics.getPrepareStatementCount()).isBetween(2L, 3L);
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions update(String token, long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        var location = result.andExpect(header().exists(HttpHeaders.LOCATION)).andReturn().getResponse();
        return objectMapper.readTree(location.getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> listing(String name) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", name);
        body.put("description", "Sahte açıklama.");
        body.put("unit", "KILOGRAM");
        body.put("priceAmount", 2450);
        body.put("stockQuantity", 80);
        return body;
    }
}
