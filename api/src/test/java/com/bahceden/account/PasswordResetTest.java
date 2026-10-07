package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * Parola sıfırlama (account-identity spec'i "Parola sıfırlama", ADR-033…036).
 *
 * <p>{@code critical}: hesap devralma yüzeyidir. Token'lar akışın gerçekten
 * gönderdiği e-postadan alınır; test kendi token'ını veritabanına yazmaz.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("POST /api/v1/accounts/password-resets ve /confirmations (parola sıfırlama)")
class PasswordResetTest {

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
    @DisplayName("Kayıtlı ve kayıtsız adres aynı yanıtı alır; e-posta yalnızca kayıtlı adrese gider (ADR-034)")
    void kayitli_ve_kayitsiz_adres_ayni_yaniti_alir() throws Exception {
        var registered = requestReset(EMAIL).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);
        mail.reset();

        var unknown = requestReset("kimse@ornek.test").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknown).isEqualTo(registered);
        assertThat(mail.sent()).as("kayıtsız adrese e-posta gönderilmez").isEmpty();
    }

    @Test
    @DisplayName("Geçerli bağlantı: parola değişir, eski parola reddedilir, yeni parola kabul edilir, yanıtta token yok")
    void gecerli_baglanti_parolayi_degistirir() throws Exception {
        var token = requestResetAndGetToken();

        confirm(token, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        signIn(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_INVALID_CREDENTIALS.name()));
        signIn(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token iki kez kullanılamaz")
    void token_iki_kez_kullanilamaz() throws Exception {
        var token = requestResetAndGetToken();
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        confirm(token, "baska-bir-gizli-parola")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));

        signIn(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Süresi dolmuş token reddedilir ve parola değişmez (1 saat, ADR-033)")
    void suresi_dolmus_token_reddedilir() throws Exception {
        var token = requestResetAndGetToken();
        clock.advance(Duration.ofMinutes(61));

        confirm(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        signIn(PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bilinmeyen token reddedilir")
    void bilinmeyen_token_reddedilir() throws Exception {
        confirm("boyle-bir-token-yok", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Yeni talep eski bağlantıyı geçersiz kılar; yalnızca en son bağlantı çalışır (ADR-033)")
    void yeni_talep_eski_baglantiyi_kapatir() throws Exception {
        var first = requestResetAndGetToken();
        var second = requestResetAndGetToken();

        confirm(first, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Kurallara uymayan parola doğrulama hatası alır ve token tüketilmez")
    void kisa_parola_tokeni_tuketmez() throws Exception {
        var token = requestResetAndGetToken();

        confirm(token, "kisa")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Sıfırlama bütün oturumları kapatır: yenileme token'ları reddedilir (ADR-035)")
    void sifirlama_oturumlari_kapatir() throws Exception {
        var phone = refreshTokenOf(signIn(PASSWORD).andExpect(status().isOk()));
        var laptop = refreshTokenOf(signIn(PASSWORD).andExpect(status().isOk()));

        confirm(requestResetAndGetToken(), NEW_PASSWORD).andExpect(status().isNoContent());

        refresh(phone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
        refresh(laptop)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Sıfırlama açık giriş kodunu geçersiz kılar; ikinci faktör atlanamaz (ADR-035)")
    void sifirlama_acik_giris_kodunu_kapatir() throws Exception {
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);
        signIn(PASSWORD).andExpect(status().isAccepted());
        var code = MailContents.loginCode(mail.lastSent().body());

        confirm(requestResetAndGetToken(), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/sessions/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", code))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        // Yeni parolayla giriş yine ikinci adıma düşer.
        signIn(NEW_PASSWORD).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Sıfırlama e-postayı doğrulanmış işaretler (ADR-036)")
    void sifirlama_epostayi_dogrular() throws Exception {
        assertThat(emailVerified()).isFalse();

        confirm(requestResetAndGetToken(), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(emailVerified()).isTrue();
    }

    @Test
    @DisplayName("Bağlantı ve değişiklik bildirimi hesabın dilinde gider; yanıt isteğin dilindedir (ADR-031, ADR-032)")
    void epostalar_hesabin_dilinde_gider() throws Exception {
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.EN);
        mail.reset();

        mockMvc.perform(post("/api/v1/accounts/password-resets")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(containsString("parola sıfırlama")));
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: reset your password");

        confirm(MailContents.passwordResetToken(mail.lastSent().body()), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: your password was changed");
        assertThat(mail.lastSent().body()).contains("/forgot-password");
    }

    @Test
    @DisplayName("Aynı e-posta için saatte üçten fazla talep 429 alır ve e-posta gönderilmez")
    void ayni_eposta_icin_hiz_siniri() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestReset(EMAIL).andExpect(status().isAccepted());
        }
        mail.reset();

        requestReset(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("Aynı istemciden saatte ondan fazla talep, farklı adreslerle de olsa 429 alır")
    void ayni_istemci_icin_hiz_siniri() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestReset("tarama" + i + "@ornek.test").andExpect(status().isAccepted());
        }

        requestReset(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
    }

    @Test
    @DisplayName("Geçersiz e-posta biçimi doğrulama hatası alır")
    void gecersiz_eposta_reddedilir() throws Exception {
        requestReset("eposta-degil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    private String requestResetAndGetToken() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted());
        return MailContents.passwordResetToken(mail.lastSent().body());
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/password-resets/confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "password", password))));
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

    private String refreshTokenOf(ResultActions signIn) throws Exception {
        var json = signIn.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("refreshToken").asText();
    }

    private boolean emailVerified() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select email_verified from accounts where email = ?", Boolean.class, EMAIL));
    }
}
