package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MailContents;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
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
 * Oturum açıkken iki adımlı girişi açma ve kapatma (account-identity spec'i
 * "E-posta ile ikinci faktör, hesap bazlı anahtarla", add-two-factor-toggle D1,
 * ADR-103).
 *
 * <p>{@code critical}: kimlik doğrulama modeli. Oturumlar gerçek giriş ucundan
 * Türkçe açılır; hesabın dili Türkçedir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("PUT /api/v1/accounts/me/two-factor (iki adımlı girişi açma ve kapatma)")
class TwoFactorToggleTest {

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
        jdbc.update("update accounts set email_verified = true where email = ?", EMAIL);
        mail.reset();
    }

    @Test
    @DisplayName("Açma: anahtar açılır, Türkçe bildirim gider, mevcut oturum sürer, sonraki giriş kod ister")
    void acma() throws Exception {
        var session = sessionJson(signIn().andExpect(status().isOk()));

        toggle(session.access(), true, PASSWORD).andExpect(status().isNoContent());

        assertThat(twoFactor()).isTrue();
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo(EMAIL);
            assertThat(sent.subject()).isEqualTo("Bahçeden: iki adımlı giriş açıldı");
            assertThat(sent.body()).contains("/forgot-password");
        });
        refresh(session.refresh()).andExpect(status().isOk());
        me(session.access()).andExpect(jsonPath("$.twoFactorEnabled").value(true));
        signIn().andExpect(status().isAccepted()).andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("Kapatma: açık giriş kodu artık kabul edilmez, bildirim gider, sonraki giriş tek adım")
    void kapatma() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);
        signIn().andExpect(status().isAccepted());
        var code = MailContents.loginCode(mail.lastSent().body());
        mail.reset();

        toggle(accessToken, false, PASSWORD).andExpect(status().isNoContent());

        assertThat(twoFactor()).isFalse();
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: iki adımlı giriş kapatıldı");
        mockMvc.perform(post("/api/v1/sessions/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", code))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        signIn().andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Yanlış mevcut parola alan hatası alır; anahtar değişmez, e-posta gitmez")
    void yanlis_parola() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();

        toggle(accessToken, true, "yanlis-gizli-parola")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        assertThat(twoFactor()).isFalse();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Doğrulanmamış e-postayla açma 422; kapalı kalır")
    void dogrulanmamis_eposta() throws Exception {
        jdbc.update("update accounts set email_verified = false where email = ?", EMAIL);
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();

        toggle(accessToken, true, PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.TWO_FACTOR_EMAIL_NOT_VERIFIED.name()));

        assertThat(twoFactor()).isFalse();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Doğrulanmamış e-postayla kapatma serbesttir: kendini kilitleme riski yok")
    void dogrulanmamis_eposta_kapatabilir() throws Exception {
        jdbc.update("update accounts set email_verified = false where email = ?", EMAIL);
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);

        toggle(accessToken, false, PASSWORD).andExpect(status().isNoContent());

        assertThat(twoFactor()).isFalse();
    }

    @Test
    @DisplayName("Aynı değer: anahtar değişmez, e-posta gitmez")
    void ayni_deger() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();

        toggle(accessToken, false, PASSWORD).andExpect(status().isNoContent());

        assertThat(twoFactor()).isFalse();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Eksik alan 400; token yok 401")
    void dogrulama_ve_kimlik() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();

        mockMvc.perform(put("/api/v1/accounts/me/two-factor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("enabled"));
        mockMvc.perform(put("/api/v1/accounts/me/two-factor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "currentPassword", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));

        assertThat(twoFactor()).isFalse();
    }

    @Test
    @DisplayName("Hız sınırı: 15 dakikada 5 istekten sonrası, parola doğru olsa bile 429")
    void hiz_siniri() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();
        for (int i = 0; i < 5; i++) {
            toggle(accessToken, true, "yanlis-gizli-parola").andExpect(status().isBadRequest());
        }

        toggle(accessToken, true, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));

        assertThat(twoFactor()).isFalse();
    }

    @Test
    @DisplayName("GET /accounts/me iki adımın durumunu döner")
    void kimlik_durumu_doner() throws Exception {
        var accessToken = sessionJson(signIn().andExpect(status().isOk())).access();

        me(accessToken).andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    private record Session(String access, String refresh) {
    }

    private ResultActions signIn() throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))));
    }

    private Session sessionJson(ResultActions result) throws Exception {
        var json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new Session(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private ResultActions toggle(String accessToken, boolean enabled, String currentPassword) throws Exception {
        return mockMvc.perform(put("/api/v1/accounts/me/two-factor")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("enabled", enabled, "currentPassword", currentPassword))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private boolean twoFactor() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select two_factor_enabled from accounts where email = ?", Boolean.class, EMAIL));
    }
}
