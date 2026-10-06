package com.bahceden.account.domain;

/**
 * Hesabın yaşam döngüsü durumu.
 *
 * <p>Bugün tek değer var: hesap kayıt anında etkin açılır (ADR-017). Enum
 * olarak tutulmasının sebebi, ikinci bir durumun (askıya alma gibi) şema göçü
 * değil tek satırlık bir kısıt değişikliği olması ([ADR-010] deseni).
 *
 * <p>Doğrulanmamış hesap diye bir <em>durum</em> yoktur: e-posta doğrulaması
 * {@code Account#isEmailVerified()} ile taşınan bir bilgidir ve girişi
 * engellemez.
 */
public enum AccountStatus {

    /** Hesap kullanılabilir. */
    ACTIVE
}
