package com.bahceden.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Mesaj dosyalarının bütünlüğü (ADR-031).
 *
 * <p>Her dil aynı anahtar kümesini taşır: eksik bir çeviri sessizce Türkçeye
 * düşmez, derleme hattında yakalanır. Yeni dil eklenince bu test de genişler.
 */
@Tag("critical")
@DisplayName("Mesaj dosyaları: her dil aynı anahtarları taşır")
class MessagesCatalogTest {

    @Test
    @DisplayName("messages_en.properties ile messages.properties aynı anahtarlara sahip ve hiçbir değer boş değil")
    void anahtar_kumeleri_esit() throws IOException {
        var turkish = load("/messages.properties");
        var english = load("/messages_en.properties");

        assertThat(english.stringPropertyNames())
                .as("İngilizce dosya Türkçe ile aynı anahtarları taşımalı")
                .containsExactlyInAnyOrderElementsOf(turkish.stringPropertyNames());
        assertThat(turkish.values()).allSatisfy(value -> assertThat(value.toString()).isNotBlank());
        assertThat(english.values()).allSatisfy(value -> assertThat(value.toString()).isNotBlank());
    }

    @Test
    @DisplayName("Desteklenmeyen ve bölgeli diller doğru eşlenir")
    void dil_eslemesi() {
        assertThat(SupportedLocale.fromTag("en")).isEqualTo(SupportedLocale.EN);
        assertThat(SupportedLocale.from(java.util.Locale.forLanguageTag("en-GB"))).isEqualTo(SupportedLocale.EN);
        assertThat(SupportedLocale.fromTag("de")).isEqualTo(SupportedLocale.TR);
        assertThat(SupportedLocale.from(null)).isEqualTo(SupportedLocale.TR);
    }

    private static Properties load(String resource) throws IOException {
        var properties = new Properties();
        try (var stream = MessagesCatalogTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
