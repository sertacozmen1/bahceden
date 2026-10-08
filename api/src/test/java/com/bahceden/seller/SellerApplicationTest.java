package com.bahceden.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.seller.domain.SellerStatus;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestcontainersConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Satıcı başvurusu, yeniden gönderim, kendi kaydı ve belgesi
 * (seller-onboarding spec'i "Satıcı başvurusu", "Belge dosyası doğrulanır…",
 * "Satıcı kendi kaydını görür…"; add-seller-onboarding D3, D4, D5, D6).
 *
 * <p>{@code critical}: yetkilendirme (oturum, sahiplik), e-posta kapısı, dosya
 * doğrulaması ve onaylı satıcı kapısı burada sabitlenir. Belgeler
 * {@code target/test-uploads} altına yazılır ve veriler tamamen sahtedir (K10).
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("/api/v1/sellers/me (satıcı başvurusu)")
class SellerApplicationTest {

    private static final String URL = "/api/v1/sellers/me";
    private static final String APPLICANT = "uretici@ornek.test";
    private static final String OTHER = "baska-uretici@ornek.test";

    /** Geçerli imzalı sahte PDF: içerik türü tespiti yalnızca ilk baytlara bakar. */
    private static final byte[] PDF = "%PDF-1.7\nsahte belge icerigi\n%%EOF".getBytes();

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private static final Path STORAGE = Path.of("target/test-uploads");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        // Önceki testlerin hız sınırı pencerelerini geride bırakır.
        clock.advance(Duration.ofDays(1));
        cleanUp();
    }

    /**
     * Satıcı satırları hesaba yabancı anahtarla bağlıdır; diğer test sınıfları
     * hesapları sildiği için bu sınıf kendi satırlarını sonrasında da temizler.
     */
    @AfterEach
    void cleanUp() {
        jdbc.execute("delete from seller_status_changes");
        jdbc.execute("delete from seller_documents");
        jdbc.execute("delete from sellers where account_id is not null");
        jdbc.execute("delete from accounts");
    }

    @Test
    @DisplayName("Doğrulanmış hesap başvurur: kayıt PENDING açılır, belge saklanır, geçiş yazılır")
    void gecerli_basvuru_pending_acar() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));

        apply(token, pdf(), "Örnek Bahçe", "Manisa", "0555 000 00 00", "CKS_CERTIFICATE")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, URL))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.displayName").value("Örnek Bahçe"))
                .andExpect(jsonPath("$.slug").value("ornek-bahce"))
                .andExpect(jsonPath("$.phone").value("+905550000000"))
                .andExpect(jsonPath("$.documentType").value("CKS_CERTIFICATE"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());

        var storageKey = jdbc.queryForObject("select storage_key from seller_documents", String.class);
        assertThat(STORAGE.resolve(storageKey)).exists();
        assertThat(jdbc.queryForObject("select content_type from seller_documents", String.class))
                .isEqualTo("application/pdf");
        assertThat(jdbc.queryForList("select from_status, to_status from seller_status_changes"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("from_status")).isNull();
                    assertThat(row.get("to_status")).isEqualTo("PENDING");
                });
    }

    @Test
    @DisplayName("Oturum yok: 401 ve kayıt oluşmaz")
    void oturumsuz_basvuru_401() throws Exception {
        mockMvc.perform(multipart(URL).file(pdf())
                        .param("displayName", "Örnek Bahçe").param("city", "Manisa")
                        .param("phone", "05550000000").param("documentType", "CKS_CERTIFICATE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.AUTH_REQUIRED.name()));

        assertThat(sellerCount()).isZero();
    }

    @Test
    @DisplayName("E-postası doğrulanmamış hesap: 422 SELLER_EMAIL_NOT_VERIFIED ve kayıt oluşmaz (D4)")
    void dogrulanmamis_eposta_422() throws Exception {
        var token = tokenFor(account(APPLICANT, false));

        validApply(token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_EMAIL_NOT_VERIFIED.name()));

        assertThat(sellerCount()).isZero();
    }

    @Test
    @DisplayName("İkinci başvuru: 409 SELLER_ALREADY_EXISTS ve mevcut kayıt değişmez")
    void ikinci_basvuru_409() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        validApply(token).andExpect(status().isCreated());

        apply(token, pdf(), "Başka Ad", "İzmir", "05550000000", "TAX_CERTIFICATE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_ALREADY_EXISTS.name()));

        assertThat(jdbc.queryForObject("select display_name from sellers where account_id is not null", String.class))
                .isEqualTo("Örnek Bahçe");
    }

    @Test
    @DisplayName("Eksik ve kurala uymayan alanlar: alan bazlı 400, hiçbir dosya yazılmaz")
    void alan_dogrulama_hatalari() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        var filesBefore = storedFileCount();

        mockMvc.perform(multipart(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("displayName", "X").param("city", "")
                        .param("phone", "05550000000").param("documentType", "PASSPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems(
                        "displayName", "city", "documentType", "document")));

        assertThat(sellerCount()).isZero();
        assertThat(storedFileCount()).isEqualTo(filesBefore);
    }

    @Test
    @DisplayName("Geçersiz telefon: alan bazlı 400 (phone)")
    void gecersiz_telefon_400() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));

        apply(token, pdf(), "Örnek Bahçe", "Manisa", "0123", "CKS_CERTIFICATE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));
    }

    @Test
    @DisplayName("Uzantısı .pdf ama içeriği PDF değil: 400 (document), dosya yazılmaz (D6)")
    void icerigi_pdf_olmayan_dosya_reddedilir() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        var filesBefore = storedFileCount();
        var fake = new MockMultipartFile("document", "belge.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes());

        apply(token, fake, "Örnek Bahçe", "Manisa", "05550000000", "CKS_CERTIFICATE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("document"));

        assertThat(sellerCount()).isZero();
        assertThat(storedFileCount()).isEqualTo(filesBefore);
    }

    @Test
    @DisplayName("Boyut sınırını aşan dosya: 400 (document), dosya yazılmaz")
    void boyut_siniri_asilir() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        var filesBefore = storedFileCount();
        var tooLarge = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, tooLarge, 0, PDF.length);

        apply(token, new MockMultipartFile("document", "buyuk.pdf", "application/pdf", tooLarge),
                "Örnek Bahçe", "Manisa", "05550000000", "CKS_CERTIFICATE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("document"));

        assertThat(storedFileCount()).isEqualTo(filesBefore);
    }

    @Test
    @DisplayName("Kendi belgesi: tespit edilen tür ve korumalı başlıklarla döner")
    void kendi_belgesi_korumali_basliklarla_doner() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        apply(token, new MockMultipartFile("document", "x.pdf", "application/pdf", PNG),
                "Örnek Bahçe", "Manisa", "05550000000", "TAX_CERTIFICATE")
                .andExpect(status().isCreated());

        mockMvc.perform(get(URL + "/document").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                // İstemcinin bildirdiği application/pdf değil, içerikten tespit edilen tür.
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"belge.png\""))
                .andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("Başka bir hesabın belgesine bu yoldan ulaşılamaz: kaydı olmayan hesap 404 alır")
    void baskasinin_belgesi_404() throws Exception {
        validApply(tokenFor(verifiedAccount(APPLICANT))).andExpect(status().isCreated());
        var otherToken = tokenFor(verifiedAccount(OTHER));

        mockMvc.perform(get(URL + "/document").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("Kaydı olmayan hesap kendi kaydını ister: 404 SELLER_NOT_FOUND")
    void kaydi_olmayan_hesap_404() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("Reddedilen başvuru yeniden gönderilir: PENDING olur, önceki belge korunur")
    void reddedilen_basvuru_yeniden_gonderilir() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        validApply(token).andExpect(status().isCreated());
        jdbc.update("update sellers set status = 'REJECTED', status_reason = 'Belge okunmuyor.' where account_id is not null");

        mockMvc.perform(multipart(HttpMethod.PUT, URL).file(pdf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("displayName", "Örnek Bahçe Düzeltilmiş").param("city", "Manisa")
                        .param("phone", "+90 555 000 00 01").param("documentType", "TAX_CERTIFICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.displayName").value("Örnek Bahçe Düzeltilmiş"))
                .andExpect(jsonPath("$.slug").value("ornek-bahce"))
                .andExpect(jsonPath("$.documentType").value("TAX_CERTIFICATE"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from seller_documents", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select from_status, to_status from seller_status_changes order by id"))
                .extracting(row -> row.get("from_status") + "->" + row.get("to_status"))
                .containsExactly("null->PENDING", "REJECTED->PENDING");
    }

    @Test
    @DisplayName("Reddedilmemiş kaydı yeniden gönderme: 409 SELLER_INVALID_TRANSITION, kayıt değişmez")
    void reddedilmemis_kayit_yeniden_gonderilemez() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        validApply(token).andExpect(status().isCreated());

        for (var state : List.of("PENDING", "APPROVED", "SUSPENDED")) {
            jdbc.update("update sellers set status = ? where account_id is not null", state);
            mockMvc.perform(multipart(HttpMethod.PUT, URL).file(pdf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("displayName", "Yeni Ad").param("city", "Manisa")
                            .param("phone", "05550000000").param("documentType", "CKS_CERTIFICATE"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.SELLER_INVALID_TRANSITION.name()));
        }
        assertThat(jdbc.queryForObject("select display_name from sellers where account_id is not null", String.class))
                .isEqualTo("Örnek Bahçe");
    }

    @Test
    @DisplayName("Aynı hesaptan saatte 5'ten fazla başvuru veya yeniden gönderim: 429")
    void hiz_siniri() throws Exception {
        var token = tokenFor(verifiedAccount(APPLICANT));
        validApply(token).andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            validApply(token).andExpect(status().isConflict());
        }

        validApply(token)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
    }

    @Test
    @DisplayName("Onaylı satıcı kapısı yalnızca APPROVED kaydı kabul eder (D5)")
    void onayli_satici_kapisi() throws Exception {
        var accountId = verifiedAccount(APPLICANT);
        validApply(tokenFor(accountId)).andExpect(status().isCreated());

        for (var state : List.of("PENDING", "REJECTED", "SUSPENDED")) {
            jdbc.update("update sellers set status = ? where account_id = ?", state, accountId);
            assertThatThrownBy(() -> sellerService.requireApprovedSeller(accountId))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiErrorCode.SELLER_NOT_APPROVED));
        }

        jdbc.update("update sellers set status = 'APPROVED' where account_id = ?", accountId);
        assertThat(sellerService.requireApprovedSeller(accountId).getStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    @Test
    @DisplayName("Üretici adından vitrin adresi: Türkçe karakterler çevrilir, çakışmada sayı eklenir")
    void slug_uretimi() throws Exception {
        assertThat(SellerService.slugify("  Çiğdem'in Şirin Bahçesi! ")).isEqualTo("cigdem-in-sirin-bahcesi");
        assertThat(SellerService.slugify("ÖĞÜ İŞÇ")).isEqualTo("ogu-isc");
        assertThat(SellerService.slugify("***")).isEqualTo("uretici");

        validApply(tokenFor(verifiedAccount(APPLICANT))).andExpect(status().isCreated());
        validApply(tokenFor(verifiedAccount(OTHER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("ornek-bahce-2"));
    }

    private ResultActions validApply(String token) throws Exception {
        return apply(token, pdf(), "Örnek Bahçe", "Manisa", "05550000000", "CKS_CERTIFICATE");
    }

    private ResultActions apply(String token, MockMultipartFile document, String displayName, String city,
                                String phone, String documentType) throws Exception {
        return mockMvc.perform(multipart(URL).file(document)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("displayName", displayName)
                .param("city", city)
                .param("phone", phone)
                .param("documentType", documentType));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("document", "belge.pdf", "application/pdf", PDF);
    }

    private long verifiedAccount(String email) {
        return account(email, true);
    }

    private long account(String email, boolean emailVerified) {
        jdbc.update("""
                insert into accounts (email, password_hash, status, email_verified)
                values (?, 'test-hash-kullanilmaz', 'ACTIVE', ?)""", email, emailVerified);
        var id = jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
        jdbc.update("insert into account_roles (account_id, role) values (?, 'ROLE_CUSTOMER')", id);
        return id;
    }

    private String tokenFor(long accountId) {
        var email = jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId);
        return accessTokens.issue(accountId, email, List.of("ROLE_CUSTOMER")).value();
    }

    private int sellerCount() {
        return jdbc.queryForObject("select count(*) from sellers where account_id is not null", Integer.class);
    }

    private static long storedFileCount() throws IOException {
        if (!Files.exists(STORAGE)) {
            return 0;
        }
        try (var files = Files.list(STORAGE)) {
            return files.count();
        }
    }
}
