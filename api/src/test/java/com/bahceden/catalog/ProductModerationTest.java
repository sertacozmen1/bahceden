package com.bahceden.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestImages;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Admin ilan denetimi (product-listings spec'i "Admin ilanı kaldırır ve
 * yeniden açar", "İlan geçmişi saklanır"; ADR-038 §3, add-product-listings D1, D7).
 *
 * <p>{@code critical}: yetkilendirme, kaldırılan ilanın vitrinden düşmesi,
 * satıcının kaldırılan ilanı kendisi açamaması ve bildirim burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("/api/v1/admin/products (ilan denetimi)")
class ProductModerationTest {

    private static final String ADMIN_URL = "/api/v1/admin/products";
    private static final String SELLER_URL = "/api/v1/sellers/me/products";
    private static final String REASON = "Fotoğraflar ilandaki ürünü göstermiyor.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private ListingFixtures fixtures;
    private String adminToken;
    private String sellerToken;
    private long sellerAccountId;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        mail.reset();
        adminToken = fixtures.token(fixtures.account("admin@ornek.test", "tr", "ROLE_CUSTOMER", "ROLE_ADMIN"),
                "ROLE_CUSTOMER", "ROLE_ADMIN");
        sellerAccountId = fixtures.approvedSeller("uretici@ornek.test");
        sellerToken = fixtures.sellerToken(sellerAccountId);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Kaldırma: REMOVED olur, vitrinden ve detaydan düşer, gerekçe satıcıya görünür, e-posta gider")
    void kaldirma() throws Exception {
        var id = publishedListing("Şeftali");
        mail.reset();

        decide(id, "remove", REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"))
                .andExpect(jsonPath("$.statusReason").value(REASON));

        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'seftali')]").isEmpty());
        mockMvc.perform(get("/api/v1/products/seftali")).andExpect(status().isNotFound());
        mockMvc.perform(get(SELLER_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.status").value("REMOVED"))
                .andExpect(jsonPath("$.statusReason").value(REASON));
        assertThat(mail.lastSent().to()).isEqualTo("uretici@ornek.test");
        assertThat(mail.lastSent().subject()).contains("kaldırıldı");
        assertThat(mail.lastSent().body()).contains(REASON).contains("/seller/products/" + id);
    }

    @Test
    @DisplayName("Satıcı kaldırılan ilanı yayına alamaz (409) ama düzenleyebilir; ilan REMOVED kalır")
    void satici_kaldirilani_acamaz_ama_duzenler() throws Exception {
        var id = publishedListing("Vişne");
        decide(id, "remove", REASON).andExpect(status().isOk());

        mockMvc.perform(post(SELLER_URL + "/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_INVALID_TRANSITION.name()));
        mockMvc.perform(put(SELLER_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Vişne (düzeltildi)", "unit", "KILOGRAM",
                                "priceAmount", 3000, "stockQuantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
    }

    @Test
    @DisplayName("Yeniden açma: PUBLISHED olur, gerekçe temizlenir, satıcıya e-posta; geçmiş sırayla tutulur")
    void yeniden_acma_ve_gecmis() throws Exception {
        var id = publishedListing("Ayva");
        decide(id, "remove", REASON).andExpect(status().isOk());
        mail.reset();

        decide(id, "reinstate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());

        mockMvc.perform(get("/api/v1/products/ayva")).andExpect(status().isOk());
        assertThat(mail.lastSent().subject()).contains("yeniden yayında");
        assertThat(jdbc.queryForList("""
                select to_status, reason from product_status_changes where product_id = ? order by id""", id))
                .extracting(row -> row.get("to_status") + "|" + row.get("reason"))
                .containsExactly("DRAFT|null", "PUBLISHED|null", "REMOVED|" + REASON, "PUBLISHED|null");
    }

    @Test
    @DisplayName("Gerekçesiz kaldırma 400; geçersiz geçiş 409; olmayan ilan 404")
    void dogrulama_ve_gecersiz_gecisler() throws Exception {
        var id = publishedListing("Erik");

        decide(id, "remove", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        decide(id, "remove", "kısa").andExpect(status().isBadRequest());
        decide(id, "reinstate", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_INVALID_TRANSITION.name()));
        decide(999_999, "remove", REASON).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Admin olmayan hesap 403 AUTH_FORBIDDEN; oturum yok 401")
    void yetkisiz_erisim() throws Exception {
        var id = publishedListing("Kayısı");

        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_FORBIDDEN.name()));
        mockMvc.perform(post(ADMIN_URL + "/" + id + "/remove").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + REASON + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_URL)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("İngilizce hesaba İngilizce e-posta; e-posta hatasında karar yine geçerli")
    void eposta_dili_ve_hatasi() throws Exception {
        jdbc.update("update accounts set locale = 'en' where id = ?", sellerAccountId);
        var id = publishedListing("Incir");
        mail.reset();

        decide(id, "remove", REASON).andExpect(status().isOk());
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: your listing was removed");

        mail.failNextSends();
        decide(id, "reinstate", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Liste: durum süzgeci, üretici ve kapak; sorgu sayısı ilan sayısından bağımsız")
    void liste_ve_sorgu_sayisi() throws Exception {
        for (int i = 1; i <= 4; i++) {
            publishedListing("Liste " + i);
        }
        var removed = publishedListing("Kaldırılan");
        decide(removed, "remove", REASON).andExpect(status().isOk());

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[0].seller.displayName").isNotEmpty())
                .andExpect(jsonPath("$.items[0].coverPhoto.cardKey").isNotEmpty());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);

        mockMvc.perform(get(ADMIN_URL).param("status", "REMOVED").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].statusReason").value(REASON));
    }

    private long publishedListing(String name) throws Exception {
        var created = mockMvc.perform(post(SELLER_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "unit", "KILOGRAM",
                                "priceAmount", 2450, "stockQuantity", 10))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(multipart(SELLER_URL + "/" + id + "/photos")
                        .file(new MockMultipartFile("photo", "f.jpg", "image/jpeg", TestImages.jpeg(20, 20)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post(SELLER_URL + "/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions decide(long id, String action, String reason) throws Exception {
        var request = post(ADMIN_URL + "/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        if (action.equals("remove")) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("reason", reason == null ? "" : reason)));
        }
        return mockMvc.perform(request);
    }
}
