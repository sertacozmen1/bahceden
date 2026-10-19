package com.bahceden.common.time;

import com.bahceden.common.i18n.SupportedLocale;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * E-postalardaki tarih-saat gösterimi (add-delivered-email D2): an Türkiye
 * saatiyle ve hesabın dilinde biçimlenir. Pazaryeri tek bölgedir (ADR-063);
 * metin saat dilimini ayrıca söyler. Ekrandaki gösterim {@code web}'in işidir.
 *
 * <p>Kalıp sabittir: {@code FormatStyle} çıktısı JDK'nın CLDR sürümüyle
 * değişir, e-posta metni öngörülebilir kalmalıdır.
 */
public final class DateTimeFormat {

    private static final String PATTERN = "d MMMM yyyy HH:mm";

    private DateTimeFormat() {
    }

    /** Örnek: 2026-10-19T11:30:00Z, tr → {@code 19 Ekim 2026 14:30}. */
    public static String format(Instant instant, SupportedLocale locale) {
        return DateTimeFormatter.ofPattern(PATTERN, locale.locale())
                .withZone(BusinessDays.TURKEY)
                .format(instant);
    }
}
