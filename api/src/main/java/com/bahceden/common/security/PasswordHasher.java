package com.bahceden.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Parola hash'leme ve doğrulama (ADR-008 §3).
 *
 * <p>BCrypt: yavaş olması kasıtlıdır, kaba kuvveti pahalı yapar. Token'lar için
 * <strong>kullanılmaz</strong> — onlar yüksek entropili rastgele değerlerdir ve
 * {@link SecureTokens} ile SHA-256 kullanır (design.md D3).
 *
 * <p>Spring'in {@link PasswordEncoder} arayüzünü sarmalar: hash biçimi tek
 * yerde durur, çağıranlar algoritmayı bilmez. Maliyet faktörü yükseltilirse
 * yalnızca bu sınıf değişir ve eski hash'ler doğrulanmaya devam eder (BCrypt
 * maliyeti hash'in içinde taşır).
 */
@Component
public class PasswordHasher {

    /**
     * Maliyet faktörü. Spring'in varsayılanı 10; 12 bugünün donanımında
     * kullanıcıyı bekletmeden kaba kuvveti belirgin şekilde pahalılaştırır.
     */
    private static final int STRENGTH = 12;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    public String hash(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    /**
     * Sabit maliyetli karşılaştırma: parola yanlış olsa da hash'leme işi
     * yapılır, böylece yanıt süresinden bilgi sızmaz.
     */
    public boolean matches(String plainPassword, String passwordHash) {
        return encoder.matches(plainPassword, passwordHash);
    }
}
