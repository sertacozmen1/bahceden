package com.bahceden.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Test e-postalarının gövdesinden kullanıcının göreceği değerleri çıkarır:
 * doğrulama bağlantısındaki token ve ikinci faktör kodu.
 */
public final class MailContents {

    private static final String VERIFICATION_LINK = "/verify-email?token=";

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?m)^\\s*(\\d{6})\\s*$");

    private MailContents() {
    }

    /** Doğrulama bağlantısındaki düz token. */
    public static String verificationToken(String body) {
        int start = body.indexOf(VERIFICATION_LINK);
        if (start < 0) {
            throw new AssertionError("E-postada doğrulama bağlantısı yok");
        }
        start += VERIFICATION_LINK.length();
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
