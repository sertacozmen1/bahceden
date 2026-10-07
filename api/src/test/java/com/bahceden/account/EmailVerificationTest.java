package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MailContents;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
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
 * E-posta doğrulama (account-identity spec'i "E-posta doğrulama (girişi
 * engellemeyen)", design.md D3, ADR-017).
 *
 * <p>{@code critical}: tek kullanımlık token kuralı burada sabitlenir. Token'lar
 * kayıt akışının gerçekten gönderdiği e-postadan alınır; test kendi token'ını
 * üretip veritabanına yazmaz.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class})
@DisplayName("POST /api/v1/accounts/verifications (e-posta doğrulama)")
class EmailVerificationTest {

    private static final String EMAIL = "alici@ornek.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private AccountService accountService;

    @BeforeEach
    void temizle() {
        jdbc.execute("delete from accounts");
        mail.reset();
    }

    @Test
    @DisplayName("Geçerli token: hesap doğrulanmış işaretlenir ve token tüketilir")
    void gecerli_token_hesabi_dogrular() throws Exception {
        var token = kayitOlVeTokenAl();

        dogrula(token).andExpect(status().isNoContent());

        assertThat(emailVerified()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from email_verification_tokens where used_at is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Token iki kez kullanılamaz")
    void token_iki_kez_kullanilamaz() throws Exception {
        var token = kayitOlVeTokenAl();
        dogrula(token).andExpect(status().isNoContent());

        dogrula(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));

        assertThat(emailVerified()).as("ilk doğrulamanın sonucu korunur").isTrue();
    }

    @Test
    @DisplayName("Süresi dolmuş token reddedilir ve hesap doğrulanmamış kalır")
    void suresi_dolmus_token_reddedilir() throws Exception {
        var token = kayitOlVeTokenAl();
        jdbc.update("update email_verification_tokens set expires_at = now() - interval '1 minute'");

        dogrula(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Bilinmeyen token reddedilir")
    void bilinmeyen_token_reddedilir() throws Exception {
        kayitOlVeTokenAl();

        dogrula("boyle-bir-token-yok")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));

        assertThat(emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Boş token doğrulama hatasıyla reddedilir")
    void bos_token_reddedilir() throws Exception {
        dogrula("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"));
    }

    private String kayitOlVeTokenAl() {
        accountService.register(EMAIL, "cok-gizli-parola");
        return MailContents.verificationToken(mail.lastSent().body());
    }

    private ResultActions dogrula(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}
                        """.formatted(token)));
    }

    private boolean emailVerified() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select email_verified from accounts where email = ?", Boolean.class, EMAIL));
    }
}
