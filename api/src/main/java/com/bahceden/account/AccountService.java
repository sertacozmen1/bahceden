package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.EmailVerificationToken;
import com.bahceden.account.domain.PasswordResetToken;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.security.PasswordHasher;
import com.bahceden.common.security.SecureTokens;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hesap açma, e-posta doğrulama ve parola sıfırlama iş kuralları.
 *
 * <p>İki kural bu sınıfın şeklini belirler:
 *
 * <ul>
 *   <li><strong>Enumeration güvenliği (design.md D4):</strong> kayıt, e-posta
 *       sistemde olsa da olmasa da aynı yanıtı döner. Fark yalnızca gönderilen
 *       e-postanın içeriğindedir.</li>
 *   <li><strong>Enumeration güvenliği parola sıfırlamada da geçerlidir
 *       (ADR-034):</strong> talep her durumda aynı sonucu verir; kayıtsız
 *       adrese e-posta gönderilmez.</li>
 *   <li><strong>E-posta girişi engellemez (ADR-017):</strong> hesap doğrudan
 *       etkin açılır. Doğrulama bağlantısı gönderilir ama tüketilmemesi girişi
 *       etkilemez; gönderim başarısız olsa bile kayıt başarılıdır.</li>
 * </ul>
 *
 * <p>E-postalar <strong>hesabın dilinde</strong> gönderilir (ADR-032); metinler
 * {@code messages*.properties} içindedir, sınıf yalnızca anahtar bilir.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /** Doğrulama bağlantısının ömrü (design.md D3). */
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    /** Sıfırlama bağlantısının ömrü: hesabı devralma yetkisi verdiği için kısa (ADR-033). */
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(1);

    private final AccountRepository accounts;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final LoginChallengeRepository loginChallenges;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final MailSender mailSender;
    private final Messages messages;
    private final BahcedenProperties properties;
    private final Clock clock;

    AccountService(AccountRepository accounts,
                   EmailVerificationTokenRepository verificationTokens,
                   PasswordResetTokenRepository passwordResetTokens,
                   RefreshTokenRepository refreshTokens,
                   LoginChallengeRepository loginChallenges,
                   PasswordHasher passwordHasher,
                   SecureTokens secureTokens,
                   MailSender mailSender,
                   Messages messages,
                   BahcedenProperties properties,
                   Clock clock) {
        this.accounts = accounts;
        this.verificationTokens = verificationTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.refreshTokens = refreshTokens;
        this.loginChallenges = loginChallenges;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Hesap açar. Çağıran hiçbir durumda farklı bir sonuç görmez: dönüş tipi
     * yok, istisna yok (doğrulama hataları controller katmanında yakalanır).
     *
     * @param locale isteğin dili; yeni hesabın dili olur. Var olan hesaba giden
     *               bildirim ise o hesabın kendi dilindedir: e-posta adresin
     *               sahibine gider, isteği yapan başkası olabilir (ADR-032).
     */
    @Transactional
    public void register(String rawEmail, String rawPassword, SupportedLocale locale) {
        var email = Account.normalizeEmail(rawEmail);
        var now = clock.instant();

        var existing = accounts.findByEmail(email);
        if (existing.isPresent()) {
            // Hesap açılmaz; bilgilendirme yalnızca adresin gerçek sahibine gider.
            var owner = existing.get();
            mailSender.send(email,
                    messages.get("mail.existingAccount.subject", owner.getLocale()),
                    messages.get("mail.existingAccount.body", owner.getLocale(), properties.webOrigin() + "/login"));
            log.info("Kayıt isteği var olan hesap için alındı: {}", maskedEmail(email));
            return;
        }

        var account = accounts.save(Account.register(email, passwordHasher.hash(rawPassword), locale, now));
        // Gönderim başarısız olsa bile hesap açık kalır (ADR-017 §4).
        sendVerificationLink(account, now);
        log.info("Hesap açıldı: {}", maskedEmail(email));
    }

    /**
     * Oturum sahibine yeni bir doğrulama bağlantısı gönderir (design.md D9).
     *
     * <p>Adres istekten değil hesaptan gelir: başkasının adresine gönderim ve
     * hesap keşfi mümkün değildir. E-posta zaten doğrulanmışsa bir şey
     * gönderilmez. Önceki bağlantılar süreleri dolana kadar geçerli kalır.
     *
     * @throws ApiException {@code AUTH_REQUIRED}: token geçerli ama hesap artık yok
     */
    @Transactional
    public void requestVerificationLink(long accountId) {
        var account = currentAccount(accountId);
        if (account.isEmailVerified()) {
            return;
        }
        sendVerificationLink(account, clock.instant());
        log.info("Doğrulama bağlantısı yeniden gönderildi: hesap {}", account.getId());
    }

    /** Tek kullanımlık token üretir, hash'ini saklar ve bağlantıyı gönderir (D3). */
    private void sendVerificationLink(Account account, Instant now) {
        var plainToken = secureTokens.newToken();
        verificationTokens.save(EmailVerificationToken.issue(
                account.getId(),
                secureTokens.hash(plainToken),
                now,
                now.plus(VERIFICATION_TOKEN_TTL)));
        mailSender.send(account.getEmail(),
                messages.get("mail.verification.subject", account.getLocale()),
                messages.get("mail.verification.body", account.getLocale(), verificationLink(plainToken)));
    }

    /**
     * Doğrulama bağlantısını tüketir ve e-postayı doğrulanmış işaretler (D3).
     *
     * <p>Girişe etkisi <strong>yoktur</strong> (ADR-017): doğrulanmış olmak bir
     * bilgidir. Geçersiz, kullanılmış ve süresi dolmuş token ayrıştırılmaz; üçü
     * de aynı hatayı döner.
     *
     * @throws ApiException {@code AUTH_TOKEN_INVALID}
     */
    @Transactional
    public void confirmVerification(String plainToken) {
        var now = clock.instant();
        var token = verificationTokens.findByTokenHash(secureTokens.hash(plainToken))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_TOKEN_INVALID, "error.verificationTokenInvalid"));

        var account = accounts.findById(token.getAccountId())
                // Yabancı anahtar bunu imkânsız kılar; düşerse veri bozulmuştur.
                .orElseThrow(() -> new IllegalStateException("Token'ın hesabı yok: " + token.getId()));
        account.markEmailVerified(now);
        token.markUsed(now);
        log.info("E-posta doğrulandı: hesap {}", account.getId());
    }

    /**
     * Parola sıfırlama bağlantısı gönderir (ADR-033, ADR-034).
     *
     * <p>Çağıran hiçbir durumda farklı bir sonuç görmez. Hesap yoksa hiçbir şey
     * yapılmaz: kayıtsız adrese e-posta gitmez, yoksa uç rastgele adreslere
     * istenmeyen posta gönderen bir araca dönüşür. Hesap varsa önceki
     * kullanılmamış bağlantılar kapatılır ve yenisi hesabın dilinde gider.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        var email = Account.normalizeEmail(rawEmail);
        var found = accounts.findByEmail(email);
        if (found.isEmpty()) {
            log.info("Parola sıfırlama isteği kayıtsız adres için alındı: {}", maskedEmail(email));
            return;
        }
        var account = found.get();
        var now = clock.instant();
        passwordResetTokens.closeOpenTokens(account.getId(), now);

        var plainToken = secureTokens.newToken();
        passwordResetTokens.save(PasswordResetToken.issue(
                account.getId(),
                secureTokens.hash(plainToken),
                now,
                now.plus(PASSWORD_RESET_TOKEN_TTL)));
        mailSender.send(account.getEmail(),
                messages.get("mail.passwordReset.subject", account.getLocale()),
                messages.get("mail.passwordReset.body", account.getLocale(), passwordResetLink(plainToken)));
        log.info("Parola sıfırlama bağlantısı gönderildi: hesap {}", account.getId());
    }

    /**
     * Sıfırlama bağlantısını tüketir ve parolayı değiştirir (ADR-035, ADR-036).
     *
     * <p>Tek transaction içinde: parola değişir, token tüketilir, hesabın bütün
     * oturumları ve açık giriş kodları kapanır, e-posta doğrulanmış işaretlenir.
     * Oturum <strong>açılmaz</strong>: giriş her zaman {@code POST /sessions}
     * üzerinden geçer, ikinci faktör atlanamaz. Geçersiz, kullanılmış ve süresi
     * dolmuş token ayrıştırılmaz.
     *
     * @throws ApiException {@code AUTH_TOKEN_INVALID}
     */
    @Transactional
    public void resetPassword(String plainToken, String rawPassword) {
        var now = clock.instant();
        var token = passwordResetTokens.findByTokenHash(secureTokens.hash(plainToken))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_TOKEN_INVALID, "error.passwordResetTokenInvalid"));

        var account = accounts.findById(token.getAccountId())
                // Yabancı anahtar bunu imkânsız kılar; düşerse veri bozulmuştur.
                .orElseThrow(() -> new IllegalStateException("Token'ın hesabı yok: " + token.getId()));
        account.changePassword(passwordHasher.hash(rawPassword), now);
        // Bağlantıyı kullanan, adresin gelen kutusuna erişmiştir (ADR-036).
        account.markEmailVerified(now);
        token.markUsed(now);
        int revoked = refreshTokens.revokeAllForAccount(account.getId(), now);
        loginChallenges.closeOpenChallenges(account.getId(), now);

        mailSender.send(account.getEmail(),
                messages.get("mail.passwordChanged.subject", account.getLocale()),
                messages.get("mail.passwordChanged.body", account.getLocale(),
                        properties.webOrigin() + "/forgot-password"));
        log.info("Parola sıfırlandı: hesap {}, {} yenileme token'ı iptal edildi", account.getId(), revoked);
    }

    /**
     * Oturum açıkken parolayı değiştirir (add-password-change D1–D3).
     *
     * <p>Mevcut parola yanlışsa ya da yeni parola mevcutla aynıysa alan hatası
     * döner ve hiçbir şey değişmez. Başarıda, sıfırlamadaki gibi (ADR-035),
     * hesabın <strong>bütün</strong> oturumları, isteği yapan dahil, ve açık
     * giriş kodları kapanır; oturum açılmaz. Sıfırlamadan farkı: e-posta
     * doğrulanmış işaretlenmez, parolayı bilmek gelen kutusuna erişimi kanıtlamaz.
     *
     * @throws ApiException {@code VALIDATION_FAILED} ({@code currentPassword} ya da
     *                      {@code newPassword} alanında); {@code AUTH_REQUIRED}: hesap artık yok
     */
    @Transactional
    public void changePassword(long accountId, String currentPassword, String newPassword) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_REQUIRED, "error.sessionInvalid"));
        if (!passwordHasher.matches(currentPassword, account.getPasswordHash())) {
            throw ApiException.invalidField("currentPassword", "validation.currentPassword.invalid");
        }
        if (passwordHasher.matches(newPassword, account.getPasswordHash())) {
            throw ApiException.invalidField("newPassword", "validation.newPassword.sameAsCurrent");
        }
        var now = clock.instant();
        account.changePassword(passwordHasher.hash(newPassword), now);
        int revoked = refreshTokens.revokeAllForAccount(account.getId(), now);
        loginChallenges.closeOpenChallenges(account.getId(), now);

        mailSender.send(account.getEmail(),
                messages.get("mail.passwordChanged.subject", account.getLocale()),
                messages.get("mail.passwordChanged.body", account.getLocale(),
                        properties.webOrigin() + "/forgot-password"));
        log.info("Parola değiştirildi: hesap {}, {} yenileme token'ı iptal edildi", account.getId(), revoked);
    }

    /**
     * Oturum sahibi iki adımlı girişi açar veya kapatır (add-two-factor-toggle D1, ADR-103).
     *
     * <p>Mevcut parola yanlışsa alan hatası döner (ADR-088 deseni). Açmak
     * doğrulanmış e-posta ister: kod bu adrese gider. Değer zaten istenen
     * durumdaysa hiçbir şey değişmez ve e-posta gitmez. Kapatmak açık giriş
     * kodlarını kapatır; açık oturumlar iki yönde de sürer. Değişiklik hesabın
     * dilinde bildirilir.
     */
    @Transactional
    public void changeTwoFactor(long accountId, boolean enabled, String currentPassword) {
        var account = currentAccount(accountId);
        if (!passwordHasher.matches(currentPassword, account.getPasswordHash())) {
            throw ApiException.invalidField("currentPassword", "validation.currentPassword.invalid");
        }
        if (enabled && !account.isEmailVerified()) {
            throw new ApiException(ApiErrorCode.TWO_FACTOR_EMAIL_NOT_VERIFIED, "error.twoFactorEmailNotVerified");
        }
        var now = clock.instant();
        if (!account.useTwoFactor(enabled, now)) {
            return;
        }
        if (!enabled) {
            loginChallenges.closeOpenChallenges(account.getId(), now);
        }
        var key = enabled ? "mail.twoFactorEnabled" : "mail.twoFactorDisabled";
        mailSender.send(account.getEmail(),
                messages.get(key + ".subject", account.getLocale()),
                messages.get(key + ".body", account.getLocale(), properties.webOrigin() + "/forgot-password"));
        log.info("İki adımlı giriş {}: hesap {}", enabled ? "açıldı" : "kapatıldı", account.getId());
    }

    /**
     * Oturum sahibinin dilini değiştirir (add-account-locale-update D1).
     *
     * <p>Dil menüsünden yapılan seçim girişi beklemeden hesaba yazılır; sonraki
     * e-postalar bu dilde gider (ADR-032). Dil aynıysa kayıt değişmez.
     */
    @Transactional
    public void changeLocale(long accountId, SupportedLocale locale) {
        currentAccount(accountId).useLocale(locale, clock.instant());
    }

    /**
     * Oturum sahibinin hesabı, veritabanından <strong>taze</strong> (D7): rol
     * değişikliği token ömrü dolmadan görünür.
     *
     * @throws ApiException {@code AUTH_REQUIRED}: token geçerli ama hesap artık yok
     */
    @Transactional(readOnly = true)
    public Account currentAccount(long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_REQUIRED, "error.sessionInvalid"));
    }

    private String verificationLink(String plainToken) {
        return properties.webOrigin() + "/verify-email?token=" + URLEncoder.encode(plainToken, StandardCharsets.UTF_8);
    }

    private String passwordResetLink(String plainToken) {
        return properties.webOrigin() + "/reset-password?token=" + URLEncoder.encode(plainToken, StandardCharsets.UTF_8);
    }

    /** Kişisel veri log'a maskeli yazılır (K10). */
    private static String maskedEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
