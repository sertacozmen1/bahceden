package com.bahceden.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Test e-postalarının gövdesinden kullanıcının göreceği değerleri çıkarır:
 * doğrulama ve parola sıfırlama bağlantılarındaki token ile ikinci faktör kodu.
 */
public final class MailContents {

    private static final String VERIFICATION_LINK = "/verify-email?token=";

    private static final String PASSWORD_RESET_LINK = "/reset-password?token=";

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?m)^\\s*(\\d{6})\\s*$");

    private MailContents() {
    }

    /** Doğrulama bağlantısındaki düz token. */
    public static String verificationToken(String body) {
        return tokenAfter(body, VERIFICATION_LINK, "doğrulama");
    }

    /** Parola sıfırlama bağlantısındaki düz token. */
    public static String passwordResetToken(String body) {
        return tokenAfter(body, PASSWORD_RESET_LINK, "parola sıfırlama");
    }

    private static String tokenAfter(String body, String link, String kind) {
        int start = body.indexOf(link);
        if (start < 0) {
            throw new AssertionError("E-postada " + kind + " bağlantısı yok");
        }
        start += link.length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return URLDecoder.decode(body.substring(start, end), StandardCharsets.UTF_8);
    }

    /** Kendi satırında duran altı haneli giriş kodu. */
    public static String loginCode(String body) {
        var matcher = SIX_DIGIT_CODE.matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("E-postada altı haneli kod yok");
        }
        return matcher.group(1);
    }
}
