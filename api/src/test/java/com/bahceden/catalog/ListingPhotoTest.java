package com.bahceden.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.TestImages;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * İlan fotoğrafları, yayın durumları ve vitrin (product-listings spec'i "İlan
 * fotoğrafları", "İlan yayın durumları"; storefront-catalog "Ürün detayı";
 * add-product-listings D1, D3–D6).
 *
 * <p>{@code critical}: görüntü işleme (yön, meta veri, saydamlık, piksel
 * sınırı), fotoğraf ucunun yalnızca fotoğraf anahtarı sunması, geçiş tablosu
 * ve vitrin görünürlüğü burada sabitlenir. Görseller programla üretilir.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class})
@DisplayName("İlan fotoğrafları, yayın ve vitrin")
class ListingPhotoTest {

    private static final String URL = "/api/v1/sellers/me/products";
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
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorage storage;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private ListingFixtures fixtures;
    private String token;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        token = fixtures.sellerToken(fixtures.approvedSeller("uretici@ornek.test"));
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Fotoğraf yüklenir: iki boyut sunulur, uzun önbellek başlığıyla; kart 400 px'i aşmaz")
    void fotograf_yukleme_ve_sunum() throws Exception {
        var id = createListing("Salkım Domates");

        var photo = upload(id, TestImages.jpeg(2000, 1000), "domates.jpg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].width").value(1600))
                .andExpect(jsonPath("$.photos[0].height").value(800));
        var cardKey = body(photo).get("photos").get(0).get("cardKey").asText();

        var served = mockMvc.perform(get("/api/v1/product-photos/" + cardKey))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("max-age=31536000"),
                                org.hamcrest.Matchers.containsString("immutable"),
                                org.hamcrest.Matchers.containsString("public"))))
                .andReturn().getResponse().getContentAsByteArray();
        var card = TestImages.decode(served);
        assertThat(Math.max(card.getWidth(), card.getHeight())).isEqualTo(400);
    }

    @Test
    @DisplayName("EXIF yönü uygulanır; çıktıda EXIF ve konum işareti yoktur")
    void exif_yonu_ve_meta_veri() throws Exception {
        var id = createListing("Elma");

        var result = upload(id, TestImages.jpegWithExif(60, 30, 6), "telefon.jpg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].width").value(30))
                .andExpect(jsonPath("$.photos[0].height").value(60));
        var largeKey = body(result).get("photos").get(0).get("largeKey").asText();

        var bytes = mockMvc.perform(get("/api/v1/product-photos/" + largeKey))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(TestImages.contains(bytes, "Exif")).isFalse();
        assertThat(TestImages.contains(bytes, TestImages.FAKE_LOCATION_MARKER)).isFalse();
        assertThat(TestImages.decode(bytes).getWidth()).isEqualTo(30);
    }

    @Test
    @DisplayName("PNG saydamlığı beyaz zemine düzleştirilir")
    void png_saydamligi_beyaza() throws Exception {
        var id = createListing("Maydanoz");

        var result = upload(id, TestImages.transparentPng(20, 20), "saydam.png").andExpect(status().isOk());
        var key = body(result).get("photos").get(0).get("cardKey").asText();

        var image = TestImages.decode(mockMvc.perform(get("/api/v1/product-photos/" + key))
                .andReturn().getResponse().getContentAsByteArray());
        var pixel = new Color(image.getRGB(10, 10));
        assertThat(pixel.getRed()).isGreaterThan(245);
        assertThat(pixel.getGreen()).isGreaterThan(245);
        assertThat(pixel.getBlue()).isGreaterThan(245);
    }

    @Test
    @DisplayName("Piksel sınırını aşan, görüntü olmayan ve boş dosya: 400 (photo), dosya yazılmaz")
    void kabul_edilmeyen_dosyalar() throws Exception {
        var id = createListing("Kayısı");
        var filesBefore = storedFileCount();

        upload(id, TestImages.pngDeclaring(10_000, 10_000), "dev.png")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("photo"));
        upload(id, "<html><script>alert(1)</script></html>".getBytes(), "sahte.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("photo"));
        upload(id, new byte[0], "bos.jpg").andExpect(status().isBadRequest());

        assertThat(storedFileCount()).isEqualTo(filesBefore);
        assertThat(jdbc.queryForObject("select count(*) from product_photos", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Altıncı fotoğraf 409 PRODUCT_PHOTO_LIMIT_REACHED")
    void bes_fotograf_siniri() throws Exception {
        var id = createListing("Armut");
        for (int i = 0; i < 5; i++) {
            upload(id, TestImages.jpeg(20, 20), "f" + i + ".jpg").andExpect(status().isOk());
        }

        upload(id, TestImages.jpeg(20, 20), "fazla.jpg")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_PHOTO_LIMIT_REACHED.name()));
    }

    @Test
    @DisplayName("Sıralama: ilk eleman kapak olur; eksik veya tekrarlı liste 400 ve sıra değişmez")
    void siralama_ve_kapak() throws Exception {
        var id = createListing("Ayva");
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            ids.add(body(upload(id, TestImages.jpeg(20, 20), "f.jpg")).get("photos").get(i).get("id").asLong());
        }

        reorder(id, List.of(ids.get(2), ids.get(0), ids.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].id").value(ids.get(2)));

        reorder(id, List.of(ids.get(0), ids.get(0), ids.get(1))).andExpect(status().isBadRequest());
        reorder(id, List.of(ids.get(0), ids.get(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("photoIds"));
        assertThat(jdbc.queryForObject("select id from product_photos where product_id = ? and position = 0",
                Long.class, id)).isEqualTo(ids.get(2));

        transition(id, "publish").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'ayva')].coverPhoto.id").value(
                        org.hamcrest.Matchers.contains(ids.get(2).intValue())));
    }

    @Test
    @DisplayName("Silme: kalanlar sıkıştırılır, dosyalar diskten silinir; yayındaki ilanın son fotoğrafı silinemez")
    void silme_ve_son_fotograf_kurali() throws Exception {
        var id = createListing("Kiraz");
        var first = body(upload(id, TestImages.jpeg(20, 20), "a.jpg")).get("photos").get(0);
        var second = body(upload(id, TestImages.jpeg(20, 20), "b.jpg")).get("photos").get(1);

        mockMvc.perform(delete(URL + "/" + id + "/photos/" + first.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(second.get("id").asLong()));
        assertThat(jdbc.queryForObject("select position from product_photos where id = ?", Integer.class,
                second.get("id").asLong())).isZero();
        assertThat(STORAGE.resolve(first.get("cardKey").asText())).doesNotExist();
        assertThat(STORAGE.resolve(first.get("largeKey").asText())).doesNotExist();

        transition(id, "publish").andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + id + "/photos/" + second.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_PHOTO_REQUIRED.name()));
    }

    @Test
    @DisplayName("Geçiş tablosu: fotoğrafsız yayın 409; yayın, çekme, arşiv, geri alma; ilk yayın tarihi korunur")
    void yayin_gecisleri() throws Exception {
        var id = createListing("Nar");

        transition(id, "publish")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_PHOTO_REQUIRED.name()));
        upload(id, TestImages.jpeg(20, 20), "nar.jpg").andExpect(status().isOk());

        var firstPublish = body(transition(id, "publish").andExpect(jsonPath("$.status").value("PUBLISHED")))
                .get("publishedAt").asText();
        transition(id, "unpublish").andExpect(jsonPath("$.status").value("DRAFT"));
        clock.advance(Duration.ofHours(3));
        // Saat ilerleyince 15 dakikalık erişim token'ı da dolar; yeni token alınır.
        token = fixtures.sellerToken(jdbc.queryForObject(
                "select id from accounts where email = 'uretici@ornek.test'", Long.class));
        transition(id, "publish").andExpect(jsonPath("$.publishedAt").value(firstPublish));
        transition(id, "archive").andExpect(jsonPath("$.status").value("ARCHIVED"));
        transition(id, "publish")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_INVALID_TRANSITION.name()));
        transition(id, "restore").andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(jdbc.queryForList("select to_status from product_status_changes where product_id = ? order by id",
                String.class, id))
                .containsExactly("DRAFT", "PUBLISHED", "DRAFT", "PUBLISHED", "ARCHIVED", "DRAFT");
    }

    @Test
    @DisplayName("Başkasının ilanına fotoğraf yükleme ve geçiş 404")
    void baskasinin_ilani_404() throws Exception {
        var id = createListing("Erik");
        var other = fixtures.sellerToken(fixtures.approvedSeller("baska@ornek.test"));

        mockMvc.perform(multipart(URL + "/" + id + "/photos")
                        .file(new MockMultipartFile("photo", "x.jpg", "image/jpeg", TestImages.jpeg(10, 10)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(URL + "/" + id + "/archive").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from product_photos", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Fotoğraf ucu: bilinmeyen anahtar 404; satıcı belgesinin anahtarı dosya diskte olsa bile 404 (D5)")
    void fotograf_ucu_yalnizca_fotograf_anahtari() throws Exception {
        mockMvc.perform(get("/api/v1/product-photos/" + "0".repeat(32))).andExpect(status().isNotFound());
        // Kodlanmış yol ayracı güvenlik duvarında reddedilir (400); hiçbir durumda dosya sunulmaz.
        mockMvc.perform(get("/api/v1/product-photos/..%2F..%2Fetc")).andExpect(status().is4xxClientError());

        var documentKey = storage.write("%PDF-1.7 gizli belge".getBytes());
        try {
            assertThat(STORAGE.resolve(documentKey)).exists();
            mockMvc.perform(get("/api/v1/product-photos/" + documentKey))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_NOT_FOUND.name()));
        } finally {
            storage.delete(documentKey);
        }
    }

    @Test
    @DisplayName("Vitrin: kapak tek ek sorguyla gelir (≤ 3 sorgu); fotoğrafsız üründe kapak boş")
    void vitrin_kapagi_ve_sorgu_sayisi() throws Exception {
        for (int i = 1; i <= 4; i++) {
            var id = createListing("Vitrin " + i);
            upload(id, TestImages.jpeg(20, 20), "v.jpg");
            transition(id, "publish").andExpect(status().isOk());
        }
        var noPhoto = createListing("Fotoğrafsız");
        jdbc.update("update products set status = 'PUBLISHED', published_at = now() where id = ?", noPhoto);

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/v1/products").param("size", "3"))
                .andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);

        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(jsonPath("$.items[?(@.slug == 'vitrin-1')].coverPhoto.cardKey").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.slug == 'fotografsiz')].coverPhoto").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("Ürün detayı: görünen ürün fotoğraflarıyla; taslak, arşiv, askıdaki satıcı ve bilinmeyen adres 404")
    void urun_detayi() throws Exception {
        var id = createListing("Dut");
        upload(id, TestImages.jpeg(20, 20), "1.jpg");
        upload(id, TestImages.jpeg(30, 20), "2.jpg");

        mockMvc.perform(get("/api/v1/products/dut")).andExpect(status().isNotFound());
        transition(id, "publish").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/dut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dut"))
                .andExpect(jsonPath("$.seller.city").value("Manisa"))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.id").doesNotExist());

        jdbc.update("update sellers set status = 'SUSPENDED' where account_id is not null");
        mockMvc.perform(get("/api/v1/products/dut"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.PRODUCT_NOT_FOUND.name()));
        jdbc.update("update sellers set status = 'APPROVED' where account_id is not null");
        transition(id, "archive").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/dut")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/boyle-bir-urun-yok")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Fotoğraf yükleme hız sınırı: aynı hesaptan saatte 60'tan fazlası 429")
    void fotograf_hiz_siniri() throws Exception {
        var id = createListing("Hız");
        for (int i = 0; i < 5; i++) {
            upload(id, TestImages.jpeg(10, 10), "h.jpg").andExpect(status().isOk());
        }
        // Sınır dolu ilanda istekler servise ulaşmadan da sayılır; hızlı ve dosya yazmaz.
        for (int i = 0; i < 55; i++) {
            upload(id, TestImages.jpeg(10, 10), "h.jpg").andExpect(status().isConflict());
        }

        upload(id, TestImages.jpeg(10, 10), "h.jpg")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.RATE_LIMITED.name()));
    }

    private long createListing(String name) throws Exception {
        var body = Map.of("name", name, "unit", "KILOGRAM", "priceAmount", 2450, "stockQuantity", 10);
        var response = mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions upload(long id, byte[] bytes, String filename) throws Exception {
        return mockMvc.perform(multipart(URL + "/" + id + "/photos")
                .file(new MockMultipartFile("photo", filename, "image/jpeg", bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private ResultActions reorder(long id, List<Long> photoIds) throws Exception {
        return mockMvc.perform(put(URL + "/" + id + "/photos/order").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("photoIds", photoIds))));
    }

    private ResultActions transition(long id, String action) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer() {
        return "Bearer " + token;
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
