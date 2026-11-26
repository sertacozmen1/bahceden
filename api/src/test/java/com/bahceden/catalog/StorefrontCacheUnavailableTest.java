package com.bahceden.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cache erişilemezken vitrin çalışır (storefront-catalog "Cache erişilemez", ADR-109).
 *
 * <p>Bu bağlamda Redis bağlantısı kapalı bir porta yönlendirilir; test
 * konteynerinin Redis'i hiç kullanılmaz. Okuma ve yazma hata vermez, değerler
 * veritabanından okunur.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class,
        StorefrontCacheUnavailableTest.UnreachableRedis.class})
@DisplayName("Vitrin cache'i erişilemez: vitrin veritabanından çalışır")
class StorefrontCacheUnavailableTest {

    private static final String SLUG = "cachesiz-domates";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private ObjectMapper objectMapper;

    private ListingFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Liste ve detay doğru veriyle döner; güncelleme hata vermez ve sonraki okuma yeni değeri gösterir")
    void cache_yokken_vitrin_calisir() throws Exception {
        var seller = fixtures.approvedSeller("uretici@ornek.test");
        long id = fixtures.publishedProduct(seller, SLUG, 2450, 10);

        mockMvc.perform(get("/api/v1/products/" + SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice.amount").value(2450));
        mockMvc.perform(get("/api/v1/products").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == '" + SLUG + "')].unitPrice.amount").value(2450));

        mockMvc.perform(put("/api/v1/sellers/me/products/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixtures.sellerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ürün " + SLUG, "unit", "KILOGRAM", "priceAmount", 3100, "stockQuantity", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/" + SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice.amount").value(3100));
    }

    /** Redis bağlantısını kapalı bir porta yönlendirir; test konteynerinin bağlantısını ezer. */
    @TestConfiguration(proxyBeanMethods = false)
    static class UnreachableRedis {

        private static final int CLOSED_PORT = 1;

        @Bean
        @Primary
        RedisConnectionDetails unreachableRedis() {
            return new RedisConnectionDetails() {
                @Override
                public Standalone getStandalone() {
                    return Standalone.of("localhost", CLOSED_PORT);
                }
            };
        }
    }
}
