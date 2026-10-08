package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Teslimat illeri ve adresler (cart-and-checkout spec'i "Teslimat adresi
 * yalnızca teslimat yapılan illerden seçilir"; add-cart-and-checkout D5).
 *
 * <p>{@code critical}: sahiplik (başkasının adresi 404) ve teslimat bölgesi
 * kuralı. Adresler sahtedir (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("/api/v1/addresses ve /api/v1/delivery/provinces")
class AddressTest {

    private static final String URL = "/api/v1/addresses";

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
    private long buyer;
    private String token;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        token = fixtures.token(buyer, "ROLE_CUSTOMER");
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Teslimat illeri herkese açık: İstanbul, İzmir, Ankara, Manisa")
    void teslimat_illeri() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("34", "35", "06", "45")))
                .andExpect(jsonPath("$.items[1].name").value("İzmir"))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @DisplayName("Teslimat yapılan ilde adres: 201, telefon E.164'e çevrilir, il adı ve deliverable döner")
    void adres_kaydi() throws Exception {
        send(post(URL), address("35"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.phone").value("+905550000000"))
                .andExpect(jsonPath("$.provinceName").value("İzmir"))
                .andExpect(jsonPath("$.deliverable").value(true));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("Teslimat listesinde olmayan il (Bursa) ve geçersiz telefon: 400, ilgili alan")
    void teslimat_disi_il_ve_telefon() throws Exception {
        send(post(URL), address("16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("provinceCode"));

        var badPhone = address("35");
        badPhone.put("phone", "123");
        send(post(URL), badPhone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));

        assertThat(jdbc.queryForObject("select count(*) from addresses", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Başkasının adresi okunamaz, değiştirilemez, silinemez: 404 ADDRESS_NOT_FOUND")
    void baskasinin_adresi() throws Exception {
        var id = idOf(send(post(URL), address("35")));
        var other = fixtures.account("baska@ornek.test", "tr", "ROLE_CUSTOMER");
        var otherToken = fixtures.token(other, "ROLE_CUSTOMER");

        mockMvc.perform(put(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(address("34"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ADDRESS_NOT_FOUND.name()));
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(jdbc.queryForObject("select province_code from addresses", String.class)).isEqualTo("35");
    }

    @Test
    @DisplayName("Güncelleme ve silme: sahibi yapar")
    void guncelleme_ve_silme() throws Exception {
        var id = idOf(send(post(URL), address("35")));

        send(put(URL + "/" + id), address("06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinceName").value("Ankara"));
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from addresses", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Hesap başına 10 adres: 11. 422 ADDRESS_LIMIT_REACHED")
    void adres_siniri() throws Exception {
        for (int i = 0; i < AddressService.MAX_ADDRESSES; i++) {
            send(post(URL), address("35")).andExpect(status().isCreated());
        }

        send(post(URL), address("35"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.ADDRESS_LIMIT_REACHED.name()));
    }

    @Test
    @DisplayName("İli teslimat listesinden çıkmış kayıtlı adres: kayıtlı kalır, deliverable=false")
    void listeden_cikan_il() throws Exception {
        jdbc.update("""
                insert into addresses (account_id, title, recipient_name, phone, province_code, district, address_line)
                values (?, 'Eski', 'Örnek Alıcı', '+905550000000', '16', 'Nilüfer', 'Ornek Mahallesi No: 1')""", buyer);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.items[0].provinceName").value("Bursa"))
                .andExpect(jsonPath("$.items[0].deliverable").value(false));
    }

    @Test
    @DisplayName("Oturum olmadan adresler 401")
    void oturumsuz() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> address(String provinceCode) {
        var body = new LinkedHashMap<String, Object>();
        body.put("title", "Ev");
        body.put("recipientName", "Örnek Alıcı");
        body.put("phone", "+90 555 000 00 00");
        body.put("provinceCode", provinceCode);
        body.put("district", "Bornova");
        body.put("addressLine", "Ornek Mahallesi, Ornek Sokak No: 1");
        body.put("postalCode", "35040");
        return body;
    }
}
