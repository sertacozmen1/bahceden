package com.bahceden.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.bahceden.support.TestcontainersConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sözleşme tazeliği (ADR-009, design.md D6).
 *
 * <p>Sözleşme Spring kodundan üretilir ve {@code shared/contracts/openapi.yaml}
 * olarak commit'lenir. Bu test ikisinin ayrışmasını engelleyen tek mekanizmadır:
 * DTO değişip sözleşme güncellenmemişse <strong>kırmızıdır</strong>.
 *
 * <p>Sözleşmeyi güncellemek için:
 * <pre>./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true</pre>
 *
 * <p>Üretilen dosya <strong>elle düzenlenmez</strong> (AGENTS.md K8): çıktı
 * yanlışsa DTO ve anotasyonlar düzeltilir, dosya yeniden üretilir.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("Sözleşme: üretilen OpenAPI dosyası commit'lenenle aynı mı")
class OpenApiContractTest {

    /** Maven testleri {@code api/} dizininden koşar; sözleşme depo kökündedir. */
    private static final Path CONTRACT_PATH = Path.of("..", "shared", "contracts", "openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Üretilen spec commit'lenen dosyayla birebir aynıdır")
    void uretilen_spec_commitlenen_dosyayla_ayni() throws Exception {
        String generated = normalize(mockMvc.perform(get("/v3/api-docs.yaml"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(generated).as("springdoc boş bir sözleşme üretmemeli").contains("openapi:");

        if (Boolean.getBoolean("openapi.write")) {
            Files.createDirectories(CONTRACT_PATH.getParent());
            Files.writeString(CONTRACT_PATH, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(CONTRACT_PATH))
                .as("""
                        Sözleşme dosyası yok: %s
                        Üretmek için: ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true""",
                        CONTRACT_PATH)
                .isTrue();

        String committed = normalize(Files.readString(CONTRACT_PATH, StandardCharsets.UTF_8));

        assertThat(committed)
                .as("""
                        Kod ile sözleşme ayrışmış (ADR-009).
                        Dosyayı ELLE düzenlemeyin (K8); yeniden üretin:
                          ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true
                        ve üretilen diff'i commit'e dahil edin.""")
                .isEqualTo(generated);
    }

    /** Satır sonu farkları (Windows / Linux) sözleşme farkı sayılmaz. */
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
