package com.bahceden.common.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Sistemin konuştuğu diller (design.md D3, D4; ADR-031, ADR-032).
 *
 * <p>Liste tek yerdedir: dil çözümleyici, hesabın saklanan dili ve
 * veritabanındaki {@code CHECK} kısıtı aynı değerleri kullanır. Yeni bir dil
 * eklemek bu enum'a bir değer, bir {@code messages_<dil>.properties} dosyası ve
 * kısıtı genişleten bir changeset demektir.
 *
 * <p>Türkçe varsayılan ve kaynak dildir; tanınmayan her dil Türkçeye düşer.
 */
public enum SupportedLocale {

    TR("tr"),
    EN("en");

    public static final SupportedLocale DEFAULT = TR;

    private final String tag;
    private final Locale locale;

    SupportedLocale(String tag) {
        this.tag = tag;
        this.locale = Locale.forLanguageTag(tag);
    }

    /** Veritabanında ve dil başlığında kullanılan etiket ({@code tr}, {@code en}). */
    public String tag() {
        return tag;
    }

    public Locale locale() {
        return locale;
    }

    /** Bölge yok sayılır ({@code en-GB} → {@code EN}); desteklenmeyen dil varsayılana düşer. */
    public static SupportedLocale from(Locale locale) {
        if (locale == null) {
            return DEFAULT;
        }
        return fromTag(locale.getLanguage());
    }

    /** Saklanan etiketten; tanınmayan değer varsayılana düşer. */
    public static SupportedLocale fromTag(String tag) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.tag.equalsIgnoreCase(tag))
                .findFirst()
                .orElse(DEFAULT);
    }

    public static List<Locale> locales() {
        return Arrays.stream(values()).map(SupportedLocale::locale).toList();
    }
}
