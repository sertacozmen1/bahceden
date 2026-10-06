package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.EmailVerificationToken;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.security.PasswordHasher;
import com.bahceden.common.security.SecureTokens;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
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
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /** Doğrulama bağlantısının ömrü (design.md D3). */
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private static final String VERIFICATION_SUBJECT = "Bahçeden: e-posta adresinizi doğrulayın";

    private static final String EXISTING_ACCOUNT_SUBJECT = "Bahçeden: hesabınız zaten var";

    private final AccountRepository accounts;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final MailSender mailSender;
    private final BahcedenProperties properties;
    private final Clock clock;

    AccountService(AccountRepository accounts,
                   EmailVerificationTokenRepository verificationTokens,
                   PasswordHasher passwordHasher,
                   SecureTokens secureTokens,
                   MailSender mailSender,
                   BahcedenProperties properties,
                   Clock clock) {
        this.accounts = accounts;
        this.verificationTokens = verificationTokens;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Hesap açar. Çağıran hiçbir durumda farklı bir sonuç görmez: dönüş tipi
     * yok, istisna yok (doğrulama hataları controller katmanında yakalanır).
     */
    @Transactional
    public void register(String rawEmail, String rawPassword) {
        var email = normalize(rawEmail);
        var now = clock.instant();

        var existing = accounts.findByEmail(email);
        if (existing.isPresent()) {
            // Hesap açılmaz; bilgilendirme yalnızca adresin gerçek sahibine gider.
            mailSender.send(email, EXISTING_ACCOUNT_SUBJECT, existingAccountBody());
            log.info("Kayıt isteği var olan hesap için alındı: {}", maskedEmail(email));
            return;
        }

        var account = accounts.save(Account.register(email, passwordHasher.hash(rawPassword), now));
        var plainToken = secureTokens.newToken();
        verificationTokens.save(EmailVerificationToken.issue(
                account.getId(),
                secureTokens.hash(plainToken),
                now,
                now.plus(VERIFICATION_TOKEN_TTL)));

        // Gönderim başarısız olsa bile hesap açık kalır (ADR-017 §4).
        mailSender.send(email, VERIFICATION_SUBJECT, verificationBody(plainToken));
        log.info("Hesap açıldı: {}", maskedEmail(email));
    }

    /**
     * E-posta normalleştirme tek yerde: küçük harf ve kırpma. Tekil kısıt bu
     * sayede gerçekten tekilliği garanti eder; {@code Ali@x} ve {@code ali@x}
     * iki hesap açamaz.
     */
    private static String normalize(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String verificationBody(String plainToken) {
        var link = properties.webOrigin() + "/dogrulama?token="
                + URLEncoder.encode(plainToken, StandardCharsets.UTF_8);
        return """
                Merhaba,

                Bahçeden hesabınızın e-posta adresini doğrulamak için aşağıdaki bağlantıyı açın:

                %s

                Bağlantı 24 saat geçerlidir ve yalnızca bir kez kullanılabilir.
                Hesabınızla doğrulamayı beklemeden giriş yapabilirsiniz.

                Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz.
                """.formatted(link);
    }

    private String existingAccountBody() {
        return """
                Merhaba,

                Bu e-posta adresiyle bir Bahçeden hesabı zaten var. Yeni bir hesap açılmadı.

                Giriş yapmak için: %s/giris

                Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz.
                """.formatted(properties.webOrigin());
    }

    /** Kişisel veri log'a maskeli yazılır (K10). */
    private static String maskedEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
