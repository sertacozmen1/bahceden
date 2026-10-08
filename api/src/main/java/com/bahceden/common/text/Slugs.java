package com.bahceden.common.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Okunabilir adlardan URL tanımlayıcısı (slug) üretimi: tek yer (K9).
 *
 * <p>Satıcı kaydı (add-seller-onboarding) ve ürün ilanı (add-product-listings)
 * aynı kuralı kullanır: Türkçe karakterler ASCII'ye çevrilir, harf ve rakam
 * dışındaki her şey tek tireye iner, baştaki ve sondaki tireler atılır.
 * Tekillik (çakışmada {@code -2}, {@code -3}) çağıranın işidir, çünkü hangi
 * tabloda arandığını o bilir.
 */
public final class Slugs {

    private static final int MAX_LENGTH = 100;

    private Slugs() {
    }

    /**
     * @param text     kullanıcının yazdığı ad
     * @param fallback ad yalnızca Latin dışı karakterlerden oluşuyorsa kullanılan taban
     */
    public static String slugify(String text, String fallback) {
        var turkishFolded = text.trim()
                .replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ç', 'c').replace('Ç', 'c')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ü', 'u').replace('Ü', 'u');
        var ascii = Normalizer.normalize(turkishFolded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (ascii.length() > MAX_LENGTH) {
            ascii = ascii.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return ascii.isEmpty() ? fallback : ascii;
    }
}
