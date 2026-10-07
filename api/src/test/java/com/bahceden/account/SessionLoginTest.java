package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.security.SecureTokens;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MailContents;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Oturum açma (account-identity spec'i "Oturum açma" ve "E-posta ile ikinci
 * faktör", design.md D1, D4, D8, ADR-017).
 *
 * <p>{@code critical}: kimlik bilgisi enumeration'ı, doğrulanmamış hesabın
 * girişi ve ikinci faktör anahtarının iki yolu burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("POST /api/v1/sessions (giriş)")
class SessionLoginTest {

    private static final String EMAIL = "alici@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccountService accountService;

    @Autowired
    private SecureTokens secureTokens;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
        mail.reset();
    }

    @Test
    @DisplayName("Doğrulanmamış hesap giriş yapabilir: e-posta doğrulaması girişin koşulu değil (ADR-017)")
    void dogrulanmamis_hesap_giris_yapabilir() throws Exception {
        assertThat(jdbc.queryForObject("select email_verified from accounts", Boolean.class)).isFalse();

        postLogin(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isNotEmpty());

        assertThat(mail.sent()).as("ikinci faktör kapalıyken e-posta gönderilmez").isEmpty();
    }

    @Test
    @DisplayName("Yenileme token'ı yalnızca hash olarak saklanır ve yeni bir aile başlatır")
    void yenileme_tokeni_hashli_saklanir() throws Exception {
        var refreshToken = issuedTokens(postLogin(EMAIL, PASSWORD)).refreshToken();
        issuedTokens(postLogin(EMAIL, PASSWORD));

        assertThat(jdbc.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, secureTokens.hash(refreshToken)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, refreshToken))
                .as("düz token hiçbir satırda saklanmamalı")
                .isZero();
        assertThat(jdbc.queryForObject("select count(distinct family_id) from refresh_tokens", Integer.class))
                .as("her giriş ayrı bir oturum zinciri başlatır (D1)")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("E-posta büyük harfli girilse de aynı hesapla giriş yapılır")
    void eposta_buyuk_kucuk_harften_bagimsiz() throws Exception {
        postLogin("ALICI@Ornek.Test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Hatalı parola ile var olmayan e-posta birebir aynı yanıtı alır (enumeration, D4)")
    void hatali_parola_ve_hatali_eposta_ayni_yaniti_doner() throws Exception {
        var wrongPasswordResponse = postLogin(EMAIL, "yanlis-parola-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_INVALID_CREDENTIALS.name()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var unknownEmailResponse = postLogin("kimse@ornek.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownEmailResponse).isEqualTo(wrongPasswordResponse);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Hata gövdesi parolayı yansıtmaz")
    void hata_govdesi_parola_icermez() throws Exception {
        var secret = "gizli-parola-sizmasin";

        postLogin(EMAIL, secret)
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(Matchers.not(Matchers.containsString(secret))));
    }

    @Test
    @DisplayName("Eksik alanlar ortak doğrulama gövdesiyle reddedilir")
    void eksik_alanlar_reddedilir() throws Exception {
        postLogin("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("İkinci faktör açık: birinci adım token döndürmez, e-postayla kod gönderir (D8)")
    void ikinci_faktor_acikken_token_donmez_kod_gonderilir() throws Exception {
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);

        postLogin(EMAIL, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class))
                .as("kod doğrulanmadan oturum açılmaz")
                .isZero();
        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);

        var code = MailContents.loginCode(mail.lastSent().body());
        var accountId = jdbc.queryForObject("select id from accounts where email = ?", Long.class, EMAIL);
        assertThat(jdbc.queryForObject("select code_hash from login_challenges", String.class))
                .as("kod hesaba bağlı hash olarak saklanır, düz değil (D8)")
                .isEqualTo(secureTokens.hash(accountId + ":" + code))
                .isNotEqualTo(code);
    }

    @Test
    @DisplayName("İkinci faktör açık ama parola yanlış: kod gönderilmez")
    void ikinci_faktor_acik_parola_yanlis_kod_gonderilmez() throws Exception {
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);

        postLogin(EMAIL, "yanlis-parola-123").andExpect(status().isUnauthorized());

        assertThat(mail.sent()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from login_challenges", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Aynı istemci ve e-postayla sınırı aşan giriş denemesi 429 alır (D5)")
    void giris_hiz_siniri_asilinca_429() throws Exception {
        for (int i = 0; i < RateLimitPolicy.LOGIN.limit(); i++) {
            postLogin(EMAIL, "yanlis-parola-123").andExpect(status().isUnauthorized());
        }

        postLogin(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));

        clock.advance(RateLimitPolicy.LOGIN.window());
        postLogin(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    private ResultActions postLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody(email, password))));
    }

    private Tokens issuedTokens(ResultActions result) throws Exception {
        var json = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, Tokens.class);
    }

    private record LoginBody(String email, String password) {
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
