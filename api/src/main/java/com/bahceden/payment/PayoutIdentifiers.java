package com.bahceden.payment;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Ödeme hesabı kimlik numaralarının doğrulaması ve maskelenmesi
 * (add-cart-and-checkout D2). Saf hesap: durum tutmaz, dış servise gitmez.
 *
 * <p>TCKN iki sağlama hanesiyle doğrulanır. VKN'nin sağlama algoritması
 * kamuya açık ama resmi değil; yalnızca biçimi denetlenir. IBAN yalnızca
 * Türkiye biçiminde ({@code TR} + 24 hane) kabul edilir ve ISO 13616 mod-97
 * sağlamasından geçer.
 */
final class PayoutIdentifiers {

    private static final Pattern TCKN = Pattern.compile("[1-9]\\d{10}");
    private static final Pattern VKN = Pattern.compile("\\d{10}");
    private static final Pattern TR_IBAN = Pattern.compile("TR\\d{24}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final BigInteger IBAN_MODULUS = BigInteger.valueOf(97);
    private static final int IBAN_GROUP = 4;
    private static final int VISIBLE_DIGITS = 4;
    private static final char MASK = '*';

    private PayoutIdentifiers() {
    }

    static boolean isValidTckn(String value) {
        if (value == null || !TCKN.matcher(value).matches()) {
            return false;
        }
        int[] digits = value.chars().map(c -> c - '0').toArray();
        int odd = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int even = digits[1] + digits[3] + digits[5] + digits[7];
        int tenth = Math.floorMod(odd * 7 - even, 10);
        int sumOfFirstTen = 0;
        for (int i = 0; i < 10; i++) {
            sumOfFirstTen += digits[i];
        }
        return digits[9] == tenth && digits[10] == sumOfFirstTen % 10;
    }

    static boolean isValidVkn(String value) {
        return value != null && VKN.matcher(value).matches();
    }

    /** Boşlukları atar ve büyük harfe çevirir; kullanıcı IBAN'ı gruplu yazabilir. */
    static String normalizeIban(String value) {
        return value == null ? "" : WHITESPACE.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** @param iban {@link #normalizeIban} ile normalleştirilmiş değer */
    static boolean isValidIban(String iban) {
        if (!TR_IBAN.matcher(iban).matches()) {
            return false;
        }
        var rearranged = iban.substring(4) + iban.substring(0, 4);
        var numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isLetter(c) ? Integer.toString(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(numeric.toString()).mod(IBAN_MODULUS).intValue() == 1;
    }

    /** {@code TR** **** **** **** **** **12 34}: ülke kodu ve son dört hane görünür. */
    static String maskIban(String iban) {
        var masked = iban.substring(0, 2)
                + String.valueOf(MASK).repeat(iban.length() - 2 - VISIBLE_DIGITS)
                + iban.substring(iban.length() - VISIBLE_DIGITS);
        var grouped = new StringBuilder();
        for (int i = 0; i < masked.length(); i += IBAN_GROUP) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(masked, i, Math.min(i + IBAN_GROUP, masked.length()));
        }
        return grouped.toString();
    }

    /** Yalnızca son dört hane görünür. */
    static String maskTaxId(String taxId) {
        return String.valueOf(MASK).repeat(taxId.length() - VISIBLE_DIGITS)
                + taxId.substring(taxId.length() - VISIBLE_DIGITS);
    }
}
