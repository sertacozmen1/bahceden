package com.bahceden.common.text;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Türkiye telefon numarası: E.164'e normalleştirme ve log için maskeleme.
 *
 * <p>Satıcı başvurusu ve teslimat adresi aynı kuralı kullanır (K9,
 * add-cart-and-checkout D5). Telefon kişisel veridir (K10): log'a yalnızca son
 * dört hane yazılır.
 */
public final class Phones {

    /** Türkiye ulusal numarası: 10 hane, 0 ile başlamaz. */
    private static final Pattern NATIONAL_NUMBER = Pattern.compile("[1-9]\\d{9}");

    /** Kullanıcının yazabileceği ayraçlar: boşluk, tire, nokta, parantez. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-.()]");

    private static final String COUNTRY_PREFIX = "+90";
    private static final String COUNTRY_CODE = "90";
    private static final int WITH_COUNTRY_CODE_LENGTH = 12;
    private static final int VISIBLE_DIGITS = 4;

    private Phones() {
    }

    /**
     * Türkiye numarasını E.164'e çevirir: ayraçlar temizlenir, {@code 0},
     * {@code 90} veya {@code +90} öneki kabul edilir.
     *
     * @return geçerli değilse boş
     */
    public static Optional<String> normalizeTurkish(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        var digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith(COUNTRY_PREFIX)) {
            digits = digits.substring(COUNTRY_PREFIX.length());
        } else if (digits.startsWith(COUNTRY_CODE) && digits.length() == WITH_COUNTRY_CODE_LENGTH) {
            digits = digits.substring(COUNTRY_CODE.length());
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return NATIONAL_NUMBER.matcher(digits).matches() ? Optional.of(COUNTRY_PREFIX + digits) : Optional.empty();
    }

    /** Log için: yalnızca son dört hane. */
    public static String masked(String phone) {
        return "***" + phone.substring(phone.length() - VISIBLE_DIGITS);
    }
}
