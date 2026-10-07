package com.bahceden.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MailContents;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Oturum sahibinin kimliği: ilk korunan uç (account-identity spec'i "Oturum
 * sahibinin kimliği", design.md D7, AGENTS.md §11.7).
 *
 * <p>{@code critical}: kimliksiz erişimin reddi, sahte veya süresi dolmuş
 * token'ın reddi ve verinin güncel kaynaktan okunması burada sabitlenir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("GET /api/v1/accounts/me (oturum sahibi)")
class CurrentAccountTest {

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
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String verificationToken;

    @BeforeEach
    void hazirla() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        jdbc.execute("delete from accounts");
        mail.reset();
        accountService.register(EMAIL, PASSWORD, SupportedLocale.TR);
        verificationToken = MailContents.verificationToken(mail.lastSent().body());
    }

    @Test
    @DisplayName("Geçerli token: e-posta, roller ve doğrulama durumu döner")
    void gecerli_token_ile_kimlik_doner() throws Exception {
        me(girisYap())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("ROLE_CUSTOMER")))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("E-posta doğrulandıktan sonra emailVerified true döner; aynı erişim token'ıyla")
    void dogrulama_sonrasi_durum_guncel_doner() throws Exception {
        var accessToken = girisYap();

        mockMvc.perform(post("/api/v1/accounts/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(verificationToken)))
                .andExpect(status().isNoContent());

        me(accessToken).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("Rol değişikliği token yenilenmeden yansır: veri güncel kaynaktan okunur (D7)")
    void rol_degisikligi_guncel_yansir() throws Exception {
        var accessToken = girisYap();
        jdbc.update("""
                insert into account_roles (account_id, role)
                select id, 'ROLE_SELLER' from accounts where email = ?""", EMAIL);

        me(accessToken)
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("ROLE_CUSTOMER", "ROLE_SELLER")));
    }

    @Test
    @DisplayName("Token yok: 401 ve ortak hata gövdesi")
    void tokensiz_istek_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Biçimi bozuk token: 401")
    void bozuk_token_401() throws Exception {
        me("bu-bir-jwt-degil")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    @DisplayName("Başka bir anahtarla imzalanmış token: 401")
    void yabanci_anahtarli_token_401() throws Exception {
        var foreignKey = "baska-bir-uygulamanin-imza-anahtari-32b".getBytes(StandardCharsets.UTF_8);
        var foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(foreignKey, "HmacSHA256")));

        me(imzala(foreignEncoder, hesapKimligi(), Instant.now().plusSeconds(600)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    @DisplayName("Süresi dolmuş token: 401")
    void suresi_dolmus_token_401() throws Exception {
        me(imzala(jwtEncoder, hesapKimligi(), Instant.now().minusSeconds(300)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    @DisplayName("Geçerli imzalı token ama hesap artık yok: 401")
    void hesabi_silinmis_token_401() throws Exception {
        var accessToken = girisYap();
        jdbc.execute("delete from accounts");

        me(accessToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
    }

    private String girisYap() throws Exception {
        var json = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(json);
        return body.get("accessToken").asText();
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private long hesapKimligi() {
        return jdbc.queryForObject("select id from accounts where email = ?", Long.class, EMAIL);
    }

    private static String imzala(JwtEncoder encoder, long accountId, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .subject(Long.toString(accountId))
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
