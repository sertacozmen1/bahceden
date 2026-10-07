package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.AccountRole;
import com.bahceden.account.domain.LoginChallenge;
import com.bahceden.account.domain.RefreshToken;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.security.PasswordHasher;
import com.bahceden.common.security.SecureTokens;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oturum açma (ADR-008, ADR-017, design.md D1, D4, D8).
 *
 * <p>Giriş iki sonuçtan birini verir ({@link LoginResult}): hesabın ikinci faktör
 * anahtarı kapalıysa (varsayılan) token'lar tek adımda; açıksa token verilmez,
 * e-posta ile tek kullanımlık kod gönderilir. E-postanın doğrulanmış olup
 * olmaması girişi <strong>etkilemez</strong> (ADR-017).
 *
 * <p>Hatalı e-posta ile hatalı parola ayırt edilemez: ikisi de aynı kodu ve
 * mesajı döner, ikisinde de bir BCrypt karşılaştırması yapılır. Böylece ne
 * yanıt ne de yanıt süresi hesabın var olduğunu söyler (D4).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Yenileme token'ının ömrü (design.md D1). */
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    /** İkinci faktör kodunun ömrü (design.md D8). */
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    private static final String INVALID_CREDENTIALS_MESSAGE = "E-posta adresi veya parola hatalı.";

    private static final String CHALLENGE_SUBJECT = "Bahçeden: giriş kodunuz";

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final LoginChallengeRepository challenges;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final AccessTokenIssuer accessTokens;
    private final MailSender mailSender;
    private final Clock clock;

    /**
     * Var olmayan hesapla girişte karşılaştırılan hash. Rastgele bir değerden
     * açılışta bir kez üretilir; hiçbir parola onunla eşleşmez, ama karşılaştırma
     * gerçek bir hesapla aynı süreyi alır.
     */
    private final String timingDecoyHash;

    SessionService(AccountRepository accounts,
                   RefreshTokenRepository refreshTokens,
                   LoginChallengeRepository challenges,
                   PasswordHasher passwordHasher,
                   SecureTokens secureTokens,
                   AccessTokenIssuer accessTokens,
                   MailSender mailSender,
                   Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.challenges = challenges;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.accessTokens = accessTokens;
        this.mailSender = mailSender;
        this.clock = clock;
        this.timingDecoyHash = passwordHasher.hash(secureTokens.newToken());
    }

    /** Girişin iki olası sonucu. */
    public sealed interface LoginResult permits Authenticated, ChallengeRequired {
    }

    /** İkinci faktör kapalı: oturum açıldı. */
    public record Authenticated(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) implements LoginResult {
    }

    /** İkinci faktör açık: kod e-postayla gönderildi, token verilmedi. */
    public record ChallengeRequired(Instant expiresAt) implements LoginResult {
    }

    /**
     * @throws ApiException {@code AUTH_INVALID_CREDENTIALS}: e-posta yok ya da parola yanlış
     */
    @Transactional
    public LoginResult login(String rawEmail, String rawPassword) {
        var account = accounts.findByEmail(Account.normalizeEmail(rawEmail));
        var hash = account.map(Account::getPasswordHash).orElse(timingDecoyHash);
        // Karşılaştırma hesap yokken de yapılır: yanıt süresi hesabı ele vermez.
        boolean passwordMatches = passwordHasher.matches(rawPassword, hash);
        if (account.isEmpty() || !passwordMatches) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }

        var found = account.get();
        var now = clock.instant();
        if (found.isTwoFactorEnabled()) {
            return issueChallenge(found, now);
        }
        log.info("Oturum açıldı: hesap {}", found.getId());
        return openSession(found, now);
    }

    /** Erişim + yenileme token'ı; yenileme token'ı yeni bir aile başlatır (D1). */
    private Authenticated openSession(Account account, Instant now) {
        var access = accessTokens.issue(
                account.getId(),
                account.getEmail(),
                account.getRoles().stream().map(AccountRole::name).sorted().toList());

        var plainRefresh = secureTokens.newToken();
        var refreshExpiresAt = now.plus(REFRESH_TOKEN_TTL);
        refreshTokens.save(RefreshToken.issue(
                account.getId(), UUID.randomUUID(), secureTokens.hash(plainRefresh), now, refreshExpiresAt));

        return new Authenticated(access.value(), access.expiresAt(), plainRefresh, refreshExpiresAt);
    }

    /**
     * Altı haneli kodu üretir, hash'ini saklar ve e-postayla gönderir (D8).
     * Gönderim başarısız olsa bile girişim kaydı durur; kullanıcı yeniden giriş
     * yaparak yeni kod alır.
     */
    private ChallengeRequired issueChallenge(Account account, Instant now) {
        String code;
        String codeHash;
        do {
            // Aynı hesapta aynı hash ≈ 10⁻⁶; tekil kısıt 500 yerine yeni kodla çözülür (D8).
            code = secureTokens.newNumericCode();
            codeHash = challengeHash(account.getId(), code);
        } while (challenges.existsByCodeHash(codeHash));

        var expiresAt = now.plus(CHALLENGE_TTL);
        challenges.save(LoginChallenge.issue(account.getId(), codeHash, now, expiresAt));
        mailSender.send(account.getEmail(), CHALLENGE_SUBJECT, challengeBody(code));
        log.info("İkinci faktör kodu gönderildi: hesap {}", account.getId());
        return new ChallengeRequired(expiresAt);
    }

    /**
     * Kod hesaba bağlı hash'lenir: kod tek başına aranmaz, her zaman belirli bir
     * hesabın girişimine karşı doğrulanır (D8).
     */
    private String challengeHash(long accountId, String code) {
        return secureTokens.hash(accountId + ":" + code);
    }

    private static String challengeBody(String code) {
        return """
                Merhaba,

                Bahçeden hesabınıza giriş için kodunuz:

                %s

                Kod 10 dakika geçerlidir ve yalnızca bir kez kullanılabilir.
                Bu girişi siz yapmadıysanız parolanızı değiştirmenizi öneririz.
                """.formatted(code);
    }
}
