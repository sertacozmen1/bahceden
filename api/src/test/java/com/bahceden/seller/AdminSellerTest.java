package com.bahceden.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.account.SessionService;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Admin kararları (seller-onboarding spec'i "Admin başvuruları karara bağlar",
 * "Kararlar bildirilir ve denetim izi saklanır"; add-seller-onboarding D5, D7).
 *
 * <p>{@code critical}: yetkilendirme (401, 403), geçiş tablosu, rol etkisi ve
 * denetim izi burada sabitlenir. Veriler tamamen sahtedir (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("/api/v1/admin/sellers (satıcı kararları)")
class AdminSellerTest {

    private static final String URL = "/api/v1/admin/sellers";
    private static final String ADMIN = "admin@ornek.test";
    private static final String APPLICANT = "uretici@ornek.test";
    private static final String PASSWORD = "cok-gizli-parola";
    private static final String REASON = "Belgedeki ad ile beyan edilen ad eşleşmiyor.";
    private static final byte[] PDF = "%PDF-1.7\nsahte belge\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private SessionService sessions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminToken;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        cleanUp();
        mail.reset();
        var adminId = account(ADMIN, "tr", "ROLE_CUSTOMER", "ROLE_ADMIN");
        adminToken = tokenFor(adminId, List.of("ROLE_ADMIN", "ROLE_CUSTOMER"));
    }

    /** Satıcı satırları hesaba bağlıdır; diğer test sınıfları hesapları siler. */
    @AfterEach
    void cleanUp() {
        jdbc.execute("delete from seller_status_changes");
        jdbc.execute("delete from seller_documents");
        jdbc.execute("delete from sellers where account_id is not null");
        jdbc.execute("delete from accounts");
    }

    @Test
    @DisplayName("Onay: PENDING → APPROVED, hesaba ROLE_SELLER eklenir, e-posta gider")
    void onay_rol_verir() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        mail.reset();

        decide(sellerId, "approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.accountEmail").value(APPLICANT));

        assertThat(roles(applicantId)).contains("ROLE_SELLER");
        assertThat(mail.lastSent().to()).isEqualTo(APPLICANT);
        assertThat(mail.lastSent().subject()).contains("onaylandı");
    }

    @Test
    @DisplayName("Gerekçeli ret: PENDING → REJECTED, gerekçe kayda yazılır, roller değişmez")
    void gerekceli_ret() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);

        decide(sellerId, "reject", REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusReason").value(REASON));

        assertThat(roles(applicantId)).containsExactly("ROLE_CUSTOMER");
        assertThat(mail.lastSent().body()).contains(REASON);
    }

    @Test
    @DisplayName("Gerekçesiz ret ve askı: 400 ve kayıt değişmez")
    void gerekcesiz_ret_ve_aski_400() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);

        decide(sellerId, "reject", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        decide(sellerId, "reject", "kısa")
                .andExpect(status().isBadRequest());

        jdbc.update("update sellers set status = 'APPROVED' where id = ?", sellerId);
        decide(sellerId, "suspend", null).andExpect(status().isBadRequest());

        assertThat(statusOf(sellerId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Askı rolü kaldırır, askıdan çıkarma geri verir")
    void aski_ve_askidan_cikarma_rol_etkisi() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        decide(sellerId, "approve", null).andExpect(status().isOk());

        decide(sellerId, "suspend", REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(roles(applicantId)).doesNotContain("ROLE_SELLER");

        decide(sellerId, "reinstate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());
        assertThat(roles(applicantId)).contains("ROLE_SELLER");
    }

    @Test
    @DisplayName("Geçersiz geçişler: 409 SELLER_INVALID_TRANSITION ve kayıt değişmez")
    void gecersiz_gecisler_409() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);

        // PENDING kayıt askıya alınamaz, askıdan çıkarılamaz.
        decide(sellerId, "suspend", REASON).andExpect(status().isConflict());
        decide(sellerId, "reinstate", null).andExpect(status().isConflict());

        decide(sellerId, "reject", REASON).andExpect(status().isOk());
        // REJECTED kayıt onaylanamaz; yalnızca sahibi yeniden gönderebilir.
        decide(sellerId, "approve", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_INVALID_TRANSITION.name()));
        assertThat(statusOf(sellerId)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Admin olmayan hesap: 403 AUTH_FORBIDDEN; oturum yok: 401")
    void yetkisiz_erisim() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        var customerToken = tokenFor(applicantId, List.of("ROLE_CUSTOMER"));

        for (var request : List.of(get(URL), get(URL + "/" + sellerId), get(URL + "/" + sellerId + "/document"),
                post(URL + "/" + sellerId + "/approve"))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_FORBIDDEN.name()))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));
        assertThat(statusOf(sellerId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Liste: durum süzgeci, ortak sayfa şekli, sunucuda sınırlı boyut ve sabit sorgu sayısı")
    void liste_suzgec_ve_sayfa() throws Exception {
        for (int i = 1; i <= 3; i++) {
            applyAs(account("uretici" + i + "@ornek.test", "tr", "ROLE_CUSTOMER"));
        }
        var rejected = applyAs(account("reddedilen@ornek.test", "tr", "ROLE_CUSTOMER"));
        decide(rejected, "reject", REASON).andExpect(status().isOk());

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(URL).param("status", "PENDING").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].status").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("PENDING"))))
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.size").value(SellerService.MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(3));
        // En çok içerik + sayım; kayıt sayısından bağımsız (N+1 yok). İlk sayfa dolmadığında
        // Spring Data sayımı atlar, bu yüzden üst sınır iddia edilir.
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);

        mockMvc.perform(get(URL).param("status", "SOMETHING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()));
    }

    @Test
    @DisplayName("Detay: başvuru, ret, yeniden gönderim ve onay geçmişte sırayla, yapanıyla görünür")
    void detay_gecmisi() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        decide(sellerId, "reject", REASON).andExpect(status().isOk());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/sellers/me")
                        .file(new MockMultipartFile("document", "belge.pdf", "application/pdf", PDF))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(applicantId, List.of("ROLE_CUSTOMER")))
                        .param("displayName", "Örnek Bahçe").param("city", "Manisa")
                        .param("phone", "05550000000").param("documentType", "TAX_CERTIFICATE"))
                .andExpect(status().isOk());
        decide(sellerId, "approve", null).andExpect(status().isOk());

        mockMvc.perform(get(URL + "/" + sellerId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+905550000000"))
                .andExpect(jsonPath("$.documentType").value("TAX_CERTIFICATE"))
                .andExpect(jsonPath("$.history[*].toStatus").value(org.hamcrest.Matchers.contains(
                        "PENDING", "REJECTED", "PENDING", "APPROVED")))
                .andExpect(jsonPath("$.history[*].actorEmail").value(org.hamcrest.Matchers.contains(
                        APPLICANT, ADMIN, APPLICANT, ADMIN)))
                .andExpect(jsonPath("$.history[1].reason").value(REASON));
    }

    @Test
    @DisplayName("Admin belgeyi korumalı başlıklarla indirir; olmayan kayıt 404")
    void admin_belgeyi_indirir() throws Exception {
        var sellerId = applyAs(account(APPLICANT, "tr", "ROLE_CUSTOMER"));

        mockMvc.perform(get(URL + "/" + sellerId + "/document")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));

        mockMvc.perform(get(URL + "/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("İngilizce hesaba ret e-postası İngilizce gider ve gerekçeyi taşır")
    void karar_epostasi_hesabin_dilinde() throws Exception {
        var sellerId = applyAs(account(APPLICANT, "en", "ROLE_CUSTOMER"));
        mail.reset();

        decide(sellerId, "reject", REASON).andExpect(status().isOk());

        assertThat(mail.lastSent().subject()).isEqualTo("Bahçeden: about your seller application");
        assertThat(mail.lastSent().body()).contains("rejected for the following reason").contains(REASON);
    }

    @Test
    @DisplayName("E-posta gönderilemezse karar yine kaydedilir ve başarılı yanıt döner")
    void eposta_hatasinda_karar_gecerli() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        mail.failNextSends();

        decide(sellerId, "approve", null).andExpect(status().isOk());

        assertThat(statusOf(sellerId)).isEqualTo("APPROVED");
        assertThat(roles(applicantId)).contains("ROLE_SELLER");
    }

    @Test
    @DisplayName("Onaydan sonra yenilenen erişim token'ı ROLE_SELLER taşır, askıdan sonra taşımaz (D5)")
    void yenilenen_token_guncel_rolu_tasir() throws Exception {
        var applicantId = account(APPLICANT, "tr", "ROLE_CUSTOMER");
        var sellerId = applyAs(applicantId);
        var signedIn = sessions.login(APPLICANT, PASSWORD, SupportedLocale.TR);
        var refreshToken = ((SessionService.Authenticated) signedIn).refreshToken();

        decide(sellerId, "approve", null).andExpect(status().isOk());
        var afterApproval = sessions.refresh(refreshToken);
        assertThat(rolesIn(afterApproval.accessToken())).contains("ROLE_SELLER");

        decide(sellerId, "suspend", REASON).andExpect(status().isOk());
        var afterSuspension = sessions.refresh(afterApproval.refreshToken());
        assertThat(rolesIn(afterSuspension.accessToken())).doesNotContain("ROLE_SELLER");
    }

    private ResultActions decide(long sellerId, String action, String reason) throws Exception {
        var request = post(URL + "/" + sellerId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        if (reason != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("reason", reason)));
        } else if (action.equals("reject") || action.equals("suspend")) {
            request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return mockMvc.perform(request);
    }

    /** Başvuruyu gerçek uçtan yapar; böylece belge ve ilk geçiş kaydı da oluşur. */
    private long applyAs(long accountId) throws Exception {
        mockMvc.perform(multipart("/api/v1/sellers/me")
                        .file(new MockMultipartFile("document", "belge.pdf", "application/pdf", PDF))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(accountId, List.of("ROLE_CUSTOMER")))
                        .param("displayName", "Örnek Bahçe").param("city", "Manisa")
                        .param("phone", "05550000000").param("documentType", "CKS_CERTIFICATE"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from sellers where account_id = ?", Long.class, accountId);
    }

    private long account(String email, String locale, String... roles) {
        jdbc.update("""
                insert into accounts (email, password_hash, status, email_verified, locale)
                values (?, ?, 'ACTIVE', true, ?)""", email, new BCryptPasswordEncoder(4).encode(PASSWORD), locale);
        var id = jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
        for (var role : roles) {
            jdbc.update("insert into account_roles (account_id, role) values (?, ?)", id, role);
        }
        return id;
    }

    private String tokenFor(long accountId, List<String> roles) {
        var email = jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId);
        return accessTokens.issue(accountId, email, roles).value();
    }

    private List<String> roles(long accountId) {
        return jdbc.queryForList("select role from account_roles where account_id = ? order by role",
                String.class, accountId);
    }

    private String statusOf(long sellerId) {
        return jdbc.queryForObject("select status from sellers where id = ?", String.class, sellerId);
    }

    private List<String> rolesIn(String jwt) throws Exception {
        var payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        var roles = objectMapper.readTree(payload).get("roles");
        return objectMapper.convertValue(roles, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }
}
