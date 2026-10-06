package com.bahceden.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Tahmin edilemez token ve kod üretimi + saklama hash'i (design.md D1, D3, D8).
 *
 * <p>Tek yardımcı: doğrulama token'ı, yenileme token'ı ve ikinci faktör kodu
 * aynı mekanizmayı kullanır. İkinci bir üretici yazılmaz (K9).
 *
 * <p><strong>Neden SHA-256, BCrypt değil?</strong> Burada hash'lenen şey parola
 * değil: 256 bit entropiyle üretilmiş rastgele bir değer. Sözlük saldırısı
 * yüzeyi yok, dolayısıyla yavaş hash yalnızca CPU yakar ve eşitlik aramasını
 * (indeksli tek sorgu) imkânsızlaştırır. Parolalar için {@link PasswordHasher}
 * kullanılır ve o BCrypt'tir.
 *
 * <p>Üretilen düz değer kullanıcıya <strong>bir kez</strong> verilir;
 * veritabanında yalnızca hash durur.
 */
@Component
public class SecureTokens {

    /** 32 bayt = 256 bit entropi. */
    private static final int TOKEN_BYTES = 32;

    /** Altı haneli kod: e-postadan elle kopyalanabilir uzunluk (D8). */
    private static final int CODE_DIGITS = 6;

    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    /** URL'de taşınabilen, tahmin edilemez token (doğrulama bağlantısı, yenileme token'ı). */
    public String newToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Altı haneli ikinci faktör kodu. Baştaki sıfırlar korunur: kullanıcı
     * e-postada gördüğü diziyi birebir girer.
     */
    public String newNumericCode() {
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(CODE_BOUND));
    }

    /** Saklama ve arama için tek yönlü özet; aynı girdi her zaman aynı çıktıyı verir. */
    public String hash(String plainValue) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plainValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException cause) {
            // SHA-256 her JVM'de zorunludur; buraya düşmek ortamın bozuk olduğunu gösterir.
            throw new IllegalStateException("SHA-256 bulunamadı", cause);
        }
    }
}
