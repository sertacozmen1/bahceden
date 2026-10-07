package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.EmailVerificationToken;
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
 * Hesap açma ve e-posta doğrulama iş kuralları.
 *
 * <p>İki kural bu sınıfın şeklini belirler:
 *
 * <ul>
 *   <li><strong>Enumeration güvenliği (design.md D4):</strong> kayıt, e-posta
 *       sistemde olsa da olmasa da aynı yanıtı döner. Fark yalnızca gönderilen
 *       e-postanın içeriğindedir.</li>
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


    private final AccountRepository accounts;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final MailSender mailSender;
    private final Messages messages;
    private final BahcedenProperties properties;
    private final Clock clock;

    AccountService(AccountRepository accounts,
                   EmailVerificationTokenRepository verificationTokens,
                   PasswordHasher passwordHasher,
                   SecureTokens secureTokens,
                   MailSender mailSender,
                   Messages messages,
                   BahcedenProperties properties,
                   Clock clock) {
        this.accounts = accounts;
        this.verificationTokens = verificationTokens;
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
                    messages.get("mail.existingAccount.body", owner.getLocale(), properties.webOrigin() + "/giris"));
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
        return properties.webOrigin() + "/dogrulama?token=" + URLEncoder.encode(plainToken, StandardCharsets.UTF_8);
    }

    /** Kişisel veri log'a maskeli yazılır (K10). */
    private static String maskedEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
