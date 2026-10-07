package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
 * Oturum yenileme ve çıkış (account-identity spec'i "Oturum yenileme ve
 * sonlandırma", design.md D1, ADR-008).
 *
 * <p>{@code critical}: rotasyon, tekrar kullanımda ailenin iptali ve çıkışın
 * gerçekten çıkış olması burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("POST /api/v1/sessions/refresh ve DELETE /api/v1/sessions/current")
class SessionRefreshTest {

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

    @BeforeEach
    void setUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
    }

    @Test
    @DisplayName("Yenileme yeni token çifti verir; sunulan token bir daha kullanılamaz")
    void yenileme_rotasyon_yapar() throws Exception {
        var first = signInAndGetToken();

        var second = issuedTokens(postRefresh(first.refreshToken()).andExpect(status().isOk()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
        assertThat(jdbc.queryForObject("select count(distinct family_id) from refresh_tokens", Integer.class))
                .as("rotasyon aynı ailede kalır")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Kullanılmış token tekrar sunulursa ailenin tamamı iptal edilir")
    void tekrar_kullanim_aileyi_iptal_eder() throws Exception {
        var first = signInAndGetToken();
        var second = issuedTokens(postRefresh(first.refreshToken()).andExpect(status().isOk()));

        postRefresh(first.refreshToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));

        // Sızıntı tespitinden sonra meşru görünen token da geçersizdir.
        postRefresh(second.refreshToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Tekrar kullanım yalnızca o aileyi iptal eder; başka cihazdaki oturum etkilenmez")
    void iptal_yalnizca_aileyi_etkiler() throws Exception {
        var phone = signInAndGetToken();
        var laptop = signInAndGetToken();
        postRefresh(phone.refreshToken()).andExpect(status().isOk());

        postRefresh(phone.refreshToken()).andExpect(status().isBadRequest());

        postRefresh(laptop.refreshToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Çıkış sonrası yenileme reddedilir")
    void cikis_sonrasi_yenileme_reddedilir() throws Exception {
        var session = signInAndGetToken();

        postLogout(session.refreshToken()).andExpect(status().isNoContent());

        postRefresh(session.refreshToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Çıkıştan sonra erişim token'ı süresi dolunca me 401 alır")
    void cikis_sonrasi_erisim_tokeni_suresi_dolunca_gecersiz() throws Exception {
        var session = signInAndGetToken();
        postLogout(session.refreshToken()).andExpect(status().isNoContent());

        // Bilinen pencere: JWT durumsuzdur, ömrü boyunca geçerli kalır (ADR-008).
        me(session.accessToken()).andExpect(status().isOk());

        clock.advance(AccessTokenIssuer.TTL.plusMinutes(2));
        me(session.accessToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
        postRefresh(session.refreshToken()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Süresi dolmuş yenileme token'ı reddedilir")
    void suresi_dolmus_yenileme_reddedilir() throws Exception {
        var session = signInAndGetToken();
        clock.advance(SessionService.REFRESH_TOKEN_TTL.plusSeconds(1));

        postRefresh(session.refreshToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    @Test
    @DisplayName("Yenilenen erişim token'ı hesabın güncel rollerini taşır")
    void yenileme_guncel_rolleri_tasir() throws Exception {
        var session = signInAndGetToken();
        jdbc.update("""
                insert into account_roles (account_id, role)
                select id, 'ROLE_SELLER' from accounts where email = ?""", EMAIL);

        var refreshed = issuedTokens(postRefresh(session.refreshToken()).andExpect(status().isOk()));

        var payload = new String(Base64.getUrlDecoder().decode(refreshed.accessToken().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(payload).get("roles").toString())
                .contains("ROLE_CUSTOMER", "ROLE_SELLER");
    }

    @Test
    @DisplayName("Bilinmeyen token ile çıkış 204 döner: token hakkında bilgi verilmez")
    void bilinmeyen_token_ile_cikis_204() throws Exception {
        postLogout("boyle-bir-token-yok").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Bilinmeyen token ile yenileme reddedilir")
    void bilinmeyen_token_ile_yenileme_reddedilir() throws Exception {
        postRefresh("boyle-bir-token-yok")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_TOKEN_INVALID.name()));
    }

    private Tokens signInAndGetToken() throws Exception {
        return issuedTokens(mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk()));
    }

    private ResultActions postRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions postLogout(String refreshToken) throws Exception {
        return mockMvc.perform(delete("/api/v1/sessions/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private Tokens issuedTokens(ResultActions result) throws Exception {
        var json = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, Tokens.class);
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
