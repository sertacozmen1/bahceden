package com.bahceden.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.SecureTokens;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import java.nio.charset.StandardCharsets;
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

/**
 * Hesap açma davranışı (account-identity spec'i, design.md D4, ADR-017).
 *
 * <p>{@code critical}: kayıt ucu bir e-postanın sistemde olup olmadığını
 * öğrenmenin en kolay yüzeyidir ve parola saklama kuralı buradan geçer
 * (AGENTS.md §6.3). Gerçek PostgreSQL'e karşı koşar (ADR-005), gerçek SMTP'ye
 * çıkmaz (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class})
@DisplayName("POST /api/v1/accounts (kayıt)")
class AccountRegistrationTest {

    private static final String EMAIL = "alici@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private SecureTokens secureTokens;

    @BeforeEach
    void temizle() {
        jdbc.execute("delete from email_verification_tokens");
        jdbc.execute("delete from account_roles");
        jdbc.execute("delete from accounts");
        mail.reset();
    }

    @Test
    @DisplayName("Yeni e-posta: hesap etkin açılır, doğrulanmamış işaretlenir, bağlantı gönderilir")
    void yeni_eposta_ile_kayit() throws Exception {
        kayitIstegi(EMAIL, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        // ADR-017: hesap doğrudan ACTIVE; doğrulama bir bilgi, giriş koşulu değil.
        assertThat(tekKolon("select status from accounts where email = ?", EMAIL)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select email_verified from accounts where email = ?", Boolean.class, EMAIL))
                .isFalse();
        assertThat(jdbc.queryForObject("select two_factor_enabled from accounts where email = ?", Boolean.class, EMAIL))
                .as("ikinci faktör varsayılan kapalı (ADR-017)")
                .isFalse();
        assertThat(tekKolon("select role from account_roles ar join accounts a on a.id = ar.account_id where a.email = ?", EMAIL))
                .isEqualTo("ROLE_CUSTOMER");
        assertThat(mail.lastSent().to()).isEqualTo(EMAIL);
        assertThat(mail.lastSent().body()).contains("/dogrulama?token=");
    }

    @Test
    @DisplayName("Doğrulama token'ı veritabanında yalnızca hash olarak durur")
    void token_hashli_saklanir() throws Exception {
        kayitIstegi(EMAIL, PASSWORD).andExpect(status().isAccepted());

        var plainToken = tokenFromMailLink(mail.lastSent().body());
        var storedHash = tekKolon("select token_hash from email_verification_tokens", null);

        assertThat(storedHash).isEqualTo(secureTokens.hash(plainToken));
        assertThat(storedHash).isNotEqualTo(plainToken);
        assertThat(jdbc.queryForObject(
                "select count(*) from email_verification_tokens where token_hash = ?", Integer.class, plainToken))
                .as("düz token hiçbir satırda saklanmamalı")
                .isZero();
    }

    @Test
    @DisplayName("Parola düz metin saklanmaz; hash BCrypt biçimindedir")
    void parola_hashlenir() throws Exception {
        kayitIstegi(EMAIL, PASSWORD).andExpect(status().isAccepted());

        var hash = tekKolon("select password_hash from accounts where email = ?", EMAIL);

        assertThat(hash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(hash).startsWith("$2");
    }

    @Test
    @DisplayName("Var olan e-posta: ikinci hesap açılmaz ve yanıt birebir aynıdır (enumeration)")
    void var_olan_eposta_ile_kayit_ayni_yaniti_doner() throws Exception {
        var ilkYanit = kayitIstegi(EMAIL, PASSWORD)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mail.reset();

        var ikinciYanit = kayitIstegi(EMAIL, "baska-bir-parola-123")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(ikinciYanit)
                .as("yanıt, e-postanın kayıtlı olduğunu açığa çıkarmamalı (D4)")
                .isEqualTo(ilkYanit);
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, EMAIL))
                .isEqualTo(1);
        assertThat(mail.lastSent().subject()).contains("zaten var");
        assertThat(mail.lastSent().body()).doesNotContain("/dogrulama?token=");
    }

    @Test
    @DisplayName("Var olan e-postada parola değişmez: ikinci kayıt mevcut hesabı ele geçirmeye izin vermez")
    void var_olan_hesabin_parolasi_degismez() throws Exception {
        kayitIstegi(EMAIL, PASSWORD).andExpect(status().isAccepted());
        var ilkHash = tekKolon("select password_hash from accounts where email = ?", EMAIL);

        kayitIstegi(EMAIL, "saldirganin-parolasi-1").andExpect(status().isAccepted());

        assertThat(tekKolon("select password_hash from accounts where email = ?", EMAIL)).isEqualTo(ilkHash);
    }

    @Test
    @DisplayName("E-posta büyük harfli girilse de aynı hesap sayılır")
    void eposta_buyuk_kucuk_harften_bagimsiz() throws Exception {
        kayitIstegi(EMAIL, PASSWORD).andExpect(status().isAccepted());

        kayitIstegi("ALICI@Ornek.Test", PASSWORD).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class))
                .as("büyük/küçük harf farkı ikinci hesap açmamalı")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Baştaki ve sondaki boşluk doğrulamada reddedilir: kırpma istemcinin işi")
    void bosluklu_eposta_reddedilir() throws Exception {
        // Sunucu katı davranır; formun gönderim öncesi kırpması gerekir (Faz 4).
        // Bu davranış bilinçli: sessizce düzeltmek yerine istemciye söylüyoruz.
        kayitIstegi("  " + EMAIL + "  ", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("E-posta gönderilemezse kayıt yine tamamlanır")
    void gonderim_hatasi_kaydi_bozmaz() throws Exception {
        mail.failNextSends();

        kayitIstegi(EMAIL, PASSWORD).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, EMAIL))
                .as("gönderim hatası hesabı geri almamalı (ADR-017 §4)")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Geçersiz e-posta ortak hata gövdesiyle reddedilir")
    void gecersiz_eposta_reddedilir() throws Exception {
        kayitIstegi("eposta-degil", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));

        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Kısa parola ortak hata gövdesiyle reddedilir")
    void kisa_parola_reddedilir() throws Exception {
        kayitIstegi(EMAIL, "kisa")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Hata gövdesi parolayı yansıtmaz")
    void hata_govdesi_parola_icermez() throws Exception {
        var secret = "gizli-parola-sizmasin";

        kayitIstegi("eposta-degil", secret)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secret))));
    }

    private org.springframework.test.web.servlet.ResultActions kayitIstegi(String email, String password)
            throws Exception {
        var body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String tekKolon(String sql, String param) {
        return param == null
                ? jdbc.queryForObject(sql, String.class)
                : jdbc.queryForObject(sql, String.class, param);
    }

    /** E-posta gövdesindeki bağlantıdan düz token'ı çıkarır. */
    private static String tokenFromMailLink(String body) {
        int start = body.indexOf("/dogrulama?token=") + "/dogrulama?token=".length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return java.net.URLDecoder.decode(body.substring(start, end), StandardCharsets.UTF_8);
    }
}
