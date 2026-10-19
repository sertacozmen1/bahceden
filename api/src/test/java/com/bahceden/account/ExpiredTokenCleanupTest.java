package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Süresi geçmiş kimlik kayıtlarının temizliği (account-identity spec'i
 * "Süresi geçmiş kimlik kayıtları temizlenir", add-stale-record-cleanup D2).
 *
 * <p>{@code critical}: yenileme token ailesinin tekrar kullanım koruması
 * (ADR-018) temizlikle zayıflamamalı. Saklama payı 7 gündür; saat sabittir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("Süresi geçmiş kimlik kayıtları temizliği")
class ExpiredTokenCleanupTest {

    private static final String EMAIL = "alici@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpiredTokenCleanup cleanup;

    @Autowired
    private ApplicationContext context;

    private long account;

    @BeforeEach
    void setUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
        account = jdbc.queryForObject("select id from accounts where email = ?", Long.class, EMAIL);
        // Kayıt bir doğrulama token'ı üretti; testler kendi satırlarıyla başlar.
        jdbc.execute("delete from email_verification_tokens");
    }

    @Test
    @DisplayName("Saklama payını geçmiş doğrulama, sıfırlama ve giriş kodu satırları silinir; pay içindekiler ve geçerliler kalır")
    void tek_kullanimlik_kayitlar() {
        var now = clock.instant();
        for (var table : new String[] {"email_verification_tokens", "password_reset_tokens"}) {
            insertToken(table, "eski-" + table, now.minus(Duration.ofDays(8)));
            insertToken(table, "payda-" + table, now.minus(Duration.ofDays(6)));
            insertToken(table, "gecerli-" + table, now.plus(Duration.ofHours(1)));
        }
        insertChallenge(now.minus(Duration.ofDays(8)));
        insertChallenge(now.minus(Duration.ofDays(1)));

        var result = cleanup.cleanOnce();

        assertThat(result.verificationTokens()).isEqualTo(1);
        assertThat(result.passwordResetTokens()).isEqualTo(1);
        assertThat(result.loginChallenges()).isEqualTo(1);
        assertThat(jdbc.queryForList("select token_hash from email_verification_tokens order by id", String.class))
                .containsExactly("payda-email_verification_tokens", "gecerli-email_verification_tokens");
        assertThat(jdbc.queryForList("select token_hash from password_reset_tokens order by id", String.class))
                .containsExactly("payda-password_reset_tokens", "gecerli-password_reset_tokens");
        assertThat(jdbc.queryForObject("select count(*) from login_challenges", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Canlı ailenin süresi geçmiş eski token'ı silinmez; tekrar sunulunca aile yine iptal edilir")
    void canli_aile_korunur() throws Exception {
        var first = signIn();
        clock.advance(Duration.ofDays(29));
        var second = refresh(first.refresh()).andExpect(status().isOk());
        var secondRefresh = field(second, "refreshToken");
        // İlk token 30 günde dolar: 9 gün sonra süresi 8 gün önce dolmuştur, ikincisi geçerlidir.
        clock.advance(Duration.ofDays(9));

        var result = cleanup.cleanOnce();

        assertThat(result.refreshTokens()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(2);
        refresh(first.refresh())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
        refresh(secondRefresh)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Bütün token'larının süresi saklama payından önce dolmuş aile silinir; diğer aile kalır")
    void suresi_gecmis_aile_silinir() throws Exception {
        var old = signIn();
        refresh(old.refresh()).andExpect(status().isOk());
        clock.advance(Duration.ofDays(31 + 7));
        var current = signIn();

        var result = cleanup.cleanOnce();

        assertThat(result.refreshTokens()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);
        refresh(current.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Testlerde zamanlayıcı kurulmaz (cleanup.enabled=false)")
    void zamanlayici_kapali() {
        assertThat(context.getBeanNamesForType(ExpiredTokenCleanupSchedule.class)).isEmpty();
    }

    private record Session(String refresh) {
    }

    private Session signIn() throws Exception {
        var result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk());
        return new Session(field(result, "refreshToken"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private String field(ResultActions result, String name) throws Exception {
        var json = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get(name).asText();
    }

    private void insertToken(String table, String hash, Instant expiresAt) {
        jdbc.update("insert into " + table + " (account_id, token_hash, expires_at, created_at) values (?, ?, ?, ?)",
                account, hash, Timestamp.from(expiresAt), Timestamp.from(expiresAt.minus(Duration.ofHours(1))));
    }

    private void insertChallenge(Instant expiresAt) {
        jdbc.update("""
                insert into login_challenges (account_id, code_hash, expires_at, attempt_count, created_at)
                values (?, ?, ?, 0, ?)""",
                account, "kod-" + UUID.randomUUID(), Timestamp.from(expiresAt),
                Timestamp.from(expiresAt.minus(Duration.ofMinutes(10))));
    }
}
