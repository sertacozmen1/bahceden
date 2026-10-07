package com.bahceden.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import java.time.Duration;
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
 * Sunucu mesajlarının dili ve hesabın dili (localization spec'i; ADR-031, ADR-032).
 *
 * <p>{@code critical}: istemcinin gösterdiği her metin bu kurallara bağlıdır ve
 * e-postanın yanlış dilde gitmesi kullanıcıya doğrudan yansır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("Yerelleştirme: istek dili ve hesabın dili")
class LocalizationTest {

    private static final String EMAIL = "dil@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void temizle() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        mail.reset();
    }

    @Test
    @DisplayName("İngilizce istekte doğrulama hatasının genel ve alan mesajı İngilizcedir; kod aynıdır")
    void ingilizce_dogrulama_hatasi() throws Exception {
        kayit(EMAIL, "kisa", "en-GB,en;q=0.9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Some of the submitted values are invalid."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Password must be between 10 and 200 characters"));
    }

    @Test
    @DisplayName("Dil bildirmeyen ve desteklenmeyen dil bildiren istek Türkçe yanıt alır")
    void dil_yoksa_ve_desteklenmiyorsa_turkce() throws Exception {
        giris(EMAIL, "yanlis-parola-12", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("E-posta adresi veya parola hatalı."));

        giris(EMAIL, "yanlis-parola-12", "de-DE,de;q=0.9")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-posta adresi veya parola hatalı."));
    }

    @Test
    @DisplayName("Güvenlik filtresinin ürettiği 401 gövdesi de isteğin dilindedir")
    void kimliksiz_istek_ingilizce() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("You need to sign in to do this."));
    }

    @Test
    @DisplayName("İngilizce kayıtta yanıt İngilizce, hesabın dili 'en' ve doğrulama e-postası İngilizce")
    void ingilizce_kayit() throws Exception {
        kayit(EMAIL, PASSWORD, "en")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "We sent a link to your email address. You can sign in to your account right away."));

        assertThat(hesapDili(EMAIL)).isEqualTo("en");
        var sent = mail.lastSent();
        assertThat(sent.subject()).isEqualTo("Bahçeden: verify your email address");
        assertThat(sent.body()).contains("To verify the email address").contains("/dogrulama?token=");
    }

    @Test
    @DisplayName("Giriş hesabın dilini günceller; giriş kodu yeni dilde gider")
    void giris_dili_gunceller() throws Exception {
        kayit(EMAIL, PASSWORD, "tr").andExpect(status().isAccepted());
        assertThat(hesapDili(EMAIL)).isEqualTo("tr");
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);
        mail.reset();

        giris(EMAIL, PASSWORD, "en")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("We sent a sign-in code to your email address."));

        assertThat(hesapDili(EMAIL)).isEqualTo("en");
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: your sign-in code");
    }

    @Test
    @DisplayName("Hatalı parola hesabın dilini değiştirmez")
    void hatali_giris_dili_degistirmez() throws Exception {
        kayit(EMAIL, PASSWORD, "tr").andExpect(status().isAccepted());

        giris(EMAIL, "yanlis-parola-12", "en").andExpect(status().isUnauthorized());

        assertThat(hesapDili(EMAIL)).isEqualTo("tr");
    }

    @Test
    @DisplayName("Var olan hesaba kayıt denemesi: yanıt isteğin dilinde, bildirim hesabın dilinde")
    void var_olan_hesap_bildirimi_hesabin_dilinde() throws Exception {
        kayit(EMAIL, PASSWORD, "tr").andExpect(status().isAccepted());
        mail.reset();

        kayit(EMAIL, PASSWORD, "en")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "We sent a link to your email address. You can sign in to your account right away."));

        assertThat(hesapDili(EMAIL)).as("kayıt denemesi hesabın dilini değiştirmez").isEqualTo("tr");
        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: hesabınız zaten var");
    }

    private ResultActions kayit(String email, String password, String acceptLanguage) throws Exception {
        var request = post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return mockMvc.perform(request);
    }

    private ResultActions giris(String email, String password, String acceptLanguage) throws Exception {
        var request = post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return mockMvc.perform(request);
    }

    private String hesapDili(String email) {
        return jdbc.queryForObject("select locale from accounts where email = ?", String.class, email);
    }
}
