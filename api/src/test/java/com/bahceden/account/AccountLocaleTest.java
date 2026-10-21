package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
 * Oturum sahibinin dilini değiştirme (localization spec'i "Hesabın dili ve
 * e-postalar", add-account-locale-update D1).
 *
 * <p>{@code critical}: yetkilendirme. Hesap yalnızca token'dan gelir; başka bir
 * hesabı adresleyen yol yoktur. Oturumlar gerçek giriş ucundan, Türkçe açılır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("PUT /api/v1/accounts/me/locale (hesabın dilini değiştirme)")
class AccountLocaleTest {

    private static final String EMAIL = "alici@ornek.test";
    private static final String OTHER_EMAIL = "diger@ornek.test";
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
    private Messages messages;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
        accountService.register(OTHER_EMAIL, PASSWORD, SupportedLocale.TR);
        mail.reset();
    }

    @Test
    @DisplayName("Dil İngilizce olur; ardından istenen doğrulama bağlantısı İngilizce gider")
    void dil_degisir_ve_eposta_yeni_dilde_gider() throws Exception {
        var accessToken = signIn(EMAIL);

        update(accessToken, "{\"locale\":\"EN\"}")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(localeOf(EMAIL)).isEqualTo("en");
        mockMvc.perform(post("/api/v1/accounts/me/verification-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr"))
                .andExpect(status().isAccepted());
        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);
        assertThat(mail.lastSent().subject())
                .isEqualTo(messages.get("mail.verification.subject", SupportedLocale.EN));
    }

    @Test
    @DisplayName("Aynı dil kaydı değiştirmez (updated_at korunur)")
    void ayni_dil_kaydi_degistirmez() throws Exception {
        var accessToken = signIn(EMAIL);
        var before = updatedAtOf(EMAIL);
        clock.advance(Duration.ofMinutes(1));

        update(accessToken, "{\"locale\":\"TR\"}").andExpect(status().isNoContent());

        assertThat(localeOf(EMAIL)).isEqualTo("tr");
        assertThat(updatedAtOf(EMAIL)).isEqualTo(before);
    }

    @Test
    @DisplayName("Tanınmayan değer ve eksik alan 400; dil değişmez")
    void gecersiz_deger_reddedilir() throws Exception {
        var accessToken = signIn(EMAIL);

        update(accessToken, "{\"locale\":\"de\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()));
        update(accessToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("locale"));

        assertThat(localeOf(EMAIL)).isEqualTo("tr");
    }

    @Test
    @DisplayName("Token yoksa 401")
    void tokensiz_istek_reddedilir() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/me/locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"EN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));

        assertThat(localeOf(EMAIL)).isEqualTo("tr");
    }

    @Test
    @DisplayName("Yalnızca oturum sahibinin dili değişir; başka hesap etkilenmez")
    void baska_hesabin_dili_degismez() throws Exception {
        update(signIn(EMAIL), "{\"locale\":\"EN\"}").andExpect(status().isNoContent());

        assertThat(localeOf(EMAIL)).isEqualTo("en");
        assertThat(localeOf(OTHER_EMAIL)).isEqualTo("tr");
    }

    private ResultActions update(String accessToken, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/accounts/me/locale")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Türkçe giriş: hesabın dili Türkçe kalır (ADR-032). */
    private String signIn(String email) throws Exception {
        var json = mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    private String localeOf(String email) {
        return jdbc.queryForObject("select locale from accounts where email = ?", String.class, email);
    }

    private Timestamp updatedAtOf(String email) {
        return jdbc.queryForObject("select updated_at from accounts where email = ?", Timestamp.class, email);
    }
}
