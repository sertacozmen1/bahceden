package com.bahceden.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahceden.common.i18n.SupportedLocale;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E-postalardaki tarih-saat (add-delivered-email D2). Türkiye saati UTC+3'tür.
 */
@Tag("critical")
@DisplayName("DateTimeFormat: Türkiye saatiyle, hesabın dilinde")
class DateTimeFormatTest {

    @Test
    @DisplayName("Aynı an iki dilde, ay adı tam ve 24 saat")
    void iki_dilde_bicimlenir() {
        var instant = Instant.parse("2026-10-19T11:30:00Z");

        assertThat(DateTimeFormat.format(instant, SupportedLocale.TR)).isEqualTo("19 Ekim 2026 14:30");
        assertThat(DateTimeFormat.format(instant, SupportedLocale.EN)).isEqualTo("19 October 2026 14:30");
    }

    @Test
    @DisplayName("UTC gece yarısına yakın an Türkiye gününe göre biçimlenir")
    void turkiye_gunune_gore() {
        assertThat(DateTimeFormat.format(Instant.parse("2026-10-19T22:15:00Z"), SupportedLocale.TR))
                .isEqualTo("20 Ekim 2026 01:15");
    }
}
