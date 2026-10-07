package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MailContents;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * İkinci faktör (account-identity spec'i "E-posta ile ikinci faktör, hesap
 * bazlı anahtarla", design.md D8, ADR-017).
 *
 * <p>{@code critical}: anahtar ürün yüzeyinde kapalıdır ama açık yolu da
 * uygulanmış ve sabitlenmiştir (K6). Anahtar testte veritabanından açılır,
 * tıpkı gerçekte olduğu gibi.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("POST /api/v1/sessions/challenge (ikinci adım)")
class LoginChallengeTest {

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
    void hazirla() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        accountService.register(EMAIL, PASSWORD);
        jdbc.update("update accounts set two_factor_enabled = true where email = ?", EMAIL);
        mail.reset();
    }

    @Test
    @DisplayName("Geçerli kod oturum açar ve kod tüketilir")
    void gecerli_kod_oturum_acar() throws Exception {
        var code = birinciAdim();

        kodGonder(EMAIL, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from login_challenges where used_at is not null", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Kod iki kez kullanılamaz")
    void kod_iki_kez_kullanilamaz() throws Exception {
        var code = birinciAdim();
        kodGonder(EMAIL, code).andExpect(status().isOk());

        kodGonder(EMAIL, code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_CODE_INVALID.name()));

        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Süresi dolmuş kod reddedilir")
    void suresi_dolmus_kod_reddedilir() throws Exception {
        var code = birinciAdim();
        clock.advance(SessionService.CHALLENGE_TTL.plusSeconds(1));

        kodGonder(EMAIL, code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_CODE_INVALID.name()));
    }

    @Test
    @DisplayName("Yalnızca son gönderilen kod geçerlidir")
    void yalnizca_son_kod_gecerli() throws Exception {
        var ilkKod = birinciAdim();
        var sonKod = birinciAdim();

        if (!ilkKod.equals(sonKod)) {
            kodGonder(EMAIL, ilkKod).andExpect(status().isUnauthorized());
        }
        kodGonder(EMAIL, sonKod).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Beş hatalı denemeden sonra doğru kod bile 429 alır")
    void hatali_deneme_siniri_asilinca_429() throws Exception {
        var code = birinciAdim();
        var yanlis = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < SessionService.MAX_CHALLENGE_ATTEMPTS; i++) {
            kodGonder(EMAIL, yanlis)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_CODE_INVALID.name()));
        }

        kodGonder(EMAIL, code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
        assertThat(jdbc.queryForObject("select attempt_count from login_challenges", Integer.class))
                .as("hatalı denemeler istisnaya rağmen kalıcı sayılır")
                .isEqualTo(SessionService.MAX_CHALLENGE_ATTEMPTS);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Başka bir hesabın e-postasıyla kod sunmak işe yaramaz")
    void baska_hesabin_epostasiyla_kod_reddedilir() throws Exception {
        accountService.register("diger@ornek.test", PASSWORD);
        var code = birinciAdim();

        kodGonder("diger@ornek.test", code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_CODE_INVALID.name()));
        kodGonder("kimse@ornek.test", code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_CODE_INVALID.name()));
    }

    @Test
    @DisplayName("Altı hane olmayan kod doğrulama hatasıyla reddedilir")
    void bicimsiz_kod_reddedilir() throws Exception {
        kodGonder(EMAIL, "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
    }

    /** Birinci adımı yapar ve e-postayla gelen kodu döner. */
    private String birinciAdim() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isAccepted());
        return MailContents.loginCode(mail.lastSent().body());
    }

    private ResultActions kodGonder(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))));
    }
}
