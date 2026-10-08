package com.bahceden.payment;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Satıcının ödeme hesabı (seller-payout-details spec'i, add-cart-and-checkout D2).
 *
 * <p>{@code critical}: onaylı satıcı kapısı, doğrulama, sağlayıcı reddi ve
 * yanıtta numaraların maskeli olması burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("/api/v1/sellers/me/payout-account (satıcı ödeme hesabı)")
class PayoutAccountTest {

    private static final String URL = "/api/v1/sellers/me/payout-account";
    private static final String TCKN = "10000000078";
    private static final String IBAN = "TR33 0006 1005 1978 6457 8413 26";

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

    private ListingFixtures fixtures;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        provider.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
        provider.reset();
    }

    @Test
    @DisplayName("Geçerli bilgiler: hesap ACTIVE, alt üye anahtarı yazılır; yanıtta numaralar maskeli")
    void gecerli_kayit() throws Exception {
        var seller = fixtures.approvedSeller("uretici@ornek.test");
        var token = fixtures.sellerToken(seller);

        var body = save(token, request("INDIVIDUAL", TCKN, null, IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.taxIdMasked").value("*******0078"))
                .andExpect(jsonPath("$.ibanMasked").value("TR** **** **** **** **** **13 26"))
                .andExpect(jsonPath("$.taxOffice").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(TCKN).doesNotContain("TR330006100519786457841326");
        assertThat(jdbc.queryForMap("select iban, status, provider_sub_merchant_key from seller_payout_accounts"))
                .containsEntry("iban", "TR330006100519786457841326")
                .containsEntry("status", "ACTIVE")
                .satisfies(row -> assertThat(row.get("provider_sub_merchant_key")).isNotNull());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Örnek Üretici"))
                .andExpect(jsonPath("$.taxIdMasked").value("*******0078"));
    }

    @Test
    @DisplayName("Güncelleme aynı alt üye anahtarıyla yapılır; şirket VKN ve vergi dairesiyle kaydolur")
    void guncelleme() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        save(token, request("INDIVIDUAL", TCKN, null, IBAN)).andExpect(status().isOk());
        var key = jdbc.queryForObject("select provider_sub_merchant_key from seller_payout_accounts", String.class);

        save(token, request("COMPANY", "1234567890", "Manisa", IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalType").value("COMPANY"))
                .andExpect(jsonPath("$.taxOffice").value("Manisa"))
                .andExpect(jsonPath("$.taxIdMasked").value("******7890"));

        assertThat(jdbc.queryForObject("select count(*) from seller_payout_accounts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select provider_sub_merchant_key from seller_payout_accounts", String.class))
                .isEqualTo(key);
    }

    @Test
    @DisplayName("Hesap yoksa 404 PAYOUT_ACCOUNT_NOT_FOUND")
    void hesap_yok() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYOUT_ACCOUNT_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("Alan hataları ilgili alana bağlanır: TCKN sağlaması, türle uyuşmayan numara, vergi dairesi, IBAN")
    void alan_hatalari() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));

        expectFieldError(save(token, request("INDIVIDUAL", "10000000079", null, IBAN)), "taxId");
        expectFieldError(save(token, request("COMPANY", TCKN, "Manisa", IBAN)), "taxId");
        expectFieldError(save(token, request("INDIVIDUAL", "1234567890", null, IBAN)), "taxId");
        expectFieldError(save(token, request("SOLE_PROPRIETOR", TCKN, " ", IBAN)), "taxOffice");
        expectFieldError(save(token, request("INDIVIDUAL", TCKN, null, "TR330006100519786457841327")), "iban");
        expectFieldError(save(token, request("PARTNERSHIP", TCKN, null, IBAN)), "legalType");

        assertThat(jdbc.queryForObject("select count(*) from seller_payout_accounts", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sağlayıcı reddederse 422 PAYOUT_ACCOUNT_REJECTED; bilgiler PROVIDER_REJECTED olarak saklanır")
    void saglayici_reddi() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        provider.rejectSubMerchants();

        save(token, request("INDIVIDUAL", TCKN, null, IBAN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYOUT_ACCOUNT_REJECTED.name()));

        assertThat(jdbc.queryForObject("select status from seller_payout_accounts", String.class))
                .isEqualTo("PROVIDER_REJECTED");
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PROVIDER_REJECTED"));
    }

    @Test
    @DisplayName("Sağlayıcıya ulaşılamazsa 502 PAYMENT_PROVIDER_ERROR; hiçbir şey kaydedilmez")
    void saglayici_ulasilamaz() throws Exception {
        var token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
        provider.makeUnavailable();

        save(token, request("INDIVIDUAL", TCKN, null, IBAN))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PAYMENT_PROVIDER_ERROR.name()));

        assertThat(jdbc.queryForObject("select count(*) from seller_payout_accounts", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Oturum yok 401; satıcı rolü yok 403 AUTH_FORBIDDEN; onaylı olmayan satıcı 403 SELLER_NOT_APPROVED")
    void yetkilendirme() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        var customer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        save(fixtures.token(customer, "ROLE_CUSTOMER"), request("INDIVIDUAL", TCKN, null, IBAN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_FORBIDDEN.name()));

        var suspended = fixtures.account("askida@ornek.test", "tr", "ROLE_CUSTOMER");
        fixtures.seller(suspended, "askida", "SUSPENDED");
        save(fixtures.sellerToken(suspended), request("INDIVIDUAL", TCKN, null, IBAN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_APPROVED.name()));

        assertThat(jdbc.queryForObject("select count(*) from seller_payout_accounts", Integer.class)).isZero();
    }

    private ResultActions save(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> request(String legalType, String taxId, String taxOffice, String iban) {
        var body = new LinkedHashMap<String, Object>();
        body.put("legalType", legalType);
        body.put("legalName", "Örnek Üretici");
        body.put("taxId", taxId);
        body.put("taxOffice", taxOffice);
        body.put("iban", iban);
        return body;
    }

    private static void expectFieldError(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
    }
}
