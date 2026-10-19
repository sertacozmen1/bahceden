package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * Oturum açıkken parola değiştirme (account-identity spec'i "Oturum açıkken
 * parola değiştirme", add-password-change D1–D3).
 *
 * <p>{@code critical}: hesap devralma ve oturum kapatma yüzeyidir. Oturumlar
 * gerçek giriş ucundan açılır; test token'ı veritabanına kendisi yazmaz.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("POST /api/v1/accounts/me/password-changes (oturum açıkken parola değiştirme)")
class PasswordChangeTest {

    private static final String EMAIL = "alici@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";
    private static final String NEW_PASSWORD = "yepyeni-gizli-parola";

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
        mail.reset();
    }

    @Test
    @DisplayName("Doğru mevcut parola: parola değişir, eski parola reddedilir, yeni parola kabul edilir, yanıtta token yok")
    void dogru_mevcut_parola_ile_degisir() throws Exception {
        var session = signIn(PASSWORD).andExpect(status().isOk());

        change(accessTokenOf(session), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        signIn(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_INVALID_CREDENTIALS.name()));
        signIn(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Değişiklik, isteği yapan oturum dahil bütün oturumları kapatır (D3)")
    void degisiklik_butun_oturumlari_kapatir() throws Exception {
        var current = signIn(PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        var other = refreshTokenOf(signIn(PASSWORD).andExpect(status().isOk()));

        change(objectMapper.readTree(current).get("accessToken").asText(), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        refresh(objectMapper.readTree(current).get("refreshToken").asText())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
        refresh(other)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Değişiklik açık giriş kodunu geçersiz kılar (D3)")
    void degisiklik_acik_giris_kodunu_kapatir() throws Exception {
        var accessToken = accessTokenOf(signIn(PASSWORD).andExpect(status().isOk()));
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);
        signIn(PASSWORD).andExpect(status().isAccepted());
        var code = MailContents.loginCode(mail.lastSent().body());

        change(accessToken, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/sessions/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", code))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("Değişiklik hesabın dilinde bildirilir; e-posta doğrulama durumu değişmez (D3)")
    void degisiklik_bildirilir_ve_dogrulama_degismez() throws Exception {
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.EN);
        // Giriş hesabın dilini isteğin diliyle yeniler (ADR-032); hesap İngilizce kalır.
        var accessToken = accessTokenOf(mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk()));
        mail.reset();

        mockMvc.perform(post("/api/v1/accounts/me/password-changes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: your password was changed");
        assertThat(mail.lastSent().body()).contains("/forgot-password");
        assertThat(emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Yanlış mevcut parola alan hatası alır; parola ve oturumlar değişmez (D2)")
    void yanlis_mevcut_parola_alan_hatasi_alir() throws Exception {
        var session = signIn(PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        change(objectMapper.readTree(session).get("accessToken").asText(), "yanlis-gizli-parola", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());

        assertThat(mail.sent()).isEmpty();
        refresh(objectMapper.readTree(session).get("refreshToken").asText()).andExpect(status().isOk());
        signIn(PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Kurallara uymayan yeni parola alan hatası alır; parola değişmez")
    void kisa_yeni_parola_reddedilir() throws Exception {
        var accessToken = accessTokenOf(signIn(PASSWORD).andExpect(status().isOk()));

        change(accessToken, PASSWORD, "kisa")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

        signIn(PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mevcut parolayla aynı yeni parola alan hatası alır; oturumlar kapanmaz (D1)")
    void ayni_parola_reddedilir() throws Exception {
        var session = signIn(PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        change(objectMapper.readTree(session).get("accessToken").asText(), PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

        refresh(objectMapper.readTree(session).get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Oturumsuz istek 401 alır")
    void oturumsuz_istek_reddedilir() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/me/password-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));

        signIn(PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Aynı hesap için 15 dakikada beşten fazla istek, mevcut parola doğru olsa bile 429 alır (D2)")
    void hesap_basina_hiz_siniri() throws Exception {
        var accessToken = accessTokenOf(signIn(PASSWORD).andExpect(status().isOk()));
        for (int i = 0; i < 5; i++) {
            change(accessToken, "yanlis-gizli-parola-" + i, NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        change(accessToken, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
        signIn(PASSWORD).andExpect(status().isOk());
    }

    private ResultActions change(String accessToken, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/me/password-changes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }

    private ResultActions signIn(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private String accessTokenOf(ResultActions signIn) throws Exception {
        return field(signIn, "accessToken");
    }

    private String refreshTokenOf(ResultActions signIn) throws Exception {
        return field(signIn, "refreshToken");
    }

    private String field(ResultActions signIn, String name) throws Exception {
        var json = signIn.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get(name).asText();
    }

    private boolean emailVerified() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select email_verified from accounts where email = ?", Boolean.class, EMAIL));
    }
}
