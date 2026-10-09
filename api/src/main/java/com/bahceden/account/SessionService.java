package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.AccountRole;
import com.bahceden.account.domain.LoginChallenge;
import com.bahceden.account.domain.RefreshToken;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.i18n.SupportedLocale;
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
 * Oturum açma, ikinci adım, yenileme ve çıkış (ADR-008, ADR-017, design.md D1, D4, D8).
 *
 * <p>Giriş iki sonuçtan birini verir ({@link LoginResult}): hesabın ikinci faktör
 * anahtarı kapalıysa (varsayılan) token'lar tek adımda; açıksa token verilmez,
 * e-posta ile tek kullanımlık kod gönderilir. E-postanın doğrulanmış olup
 * olmaması girişi <strong>etkilemez</strong> (ADR-017).
 *
 * <p>Hatalı e-posta ile hatalı parola ayırt edilemez: ikisi de aynı kodu ve
 * mesajı döner, ikisinde de bir BCrypt karşılaştırması yapılır. Böylece ne
 * yanıt ne de yanıt süresi hesabın var olduğunu söyler (D4).
 *
 * <p>Başarılı parola adımı hesabın dilini isteğin diliyle günceller; giriş
 * kodu e-postası bu dilde gider (ADR-032). Hata metinleri anahtar olarak
 * fırlatılır ve isteğin dilinde çözülür (ADR-031).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Yenileme token'ının ömrü (design.md D1). */
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    /**
     * Kullanılmış token'ın sızıntı sayılmadan yeniden kabul edildiği süre
     * (ADR-069). Aynı anda açılan iki sekmenin yenileme yarışını kapsar.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    /** İkinci faktör kodunun ömrü (design.md D8). */
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    /** Bir giriş girişimi için izin verilen hatalı kod sayısı (design.md D8). */
    static final int MAX_CHALLENGE_ATTEMPTS = 5;

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final LoginChallengeRepository challenges;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final AccessTokenIssuer accessTokens;
    private final MailSender mailSender;
    private final Messages messages;
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
                   Messages messages,
                   Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.challenges = challenges;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.accessTokens = accessTokens;
        this.mailSender = mailSender;
        this.messages = messages;
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
     * @param locale isteğin dili; parola doğruysa hesabın dili olur (ADR-032)
     * @throws ApiException {@code AUTH_INVALID_CREDENTIALS}: e-posta yok ya da parola yanlış
     */
    @Transactional
    public LoginResult login(String rawEmail, String rawPassword, SupportedLocale locale) {
        var account = accounts.findByEmail(Account.normalizeEmail(rawEmail));
        var hash = account.map(Account::getPasswordHash).orElse(timingDecoyHash);
        // Karşılaştırma hesap yokken de yapılır: yanıt süresi hesabı ele vermez.
        boolean passwordMatches = passwordHasher.matches(rawPassword, hash);
        if (account.isEmpty() || !passwordMatches) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS, "error.invalidCredentials");
        }

        var found = account.get();
        var now = clock.instant();
        found.useLocale(locale, now);
        if (found.isTwoFactorEnabled()) {
            return issueChallenge(found, now);
        }
        log.info("Oturum açıldı: hesap {}", found.getId());
        return issueTokens(found, UUID.randomUUID(), now);
    }

    /**
     * İkinci adım: e-postayla gönderilen kodu hesabın <strong>en son</strong> açık
     * girişimine karşı doğrular (D8). Hatalı deneme sayılır ve sayı, istisna
     * fırlatılsa da kalıcıdır ({@code noRollbackFor}); sınır dolunca doğru kod
     * bile reddedilir.
     *
     * @throws ApiException {@code AUTH_CODE_INVALID} ya da {@code RATE_LIMITED}
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Authenticated completeChallenge(String rawEmail, String code) {
        var now = clock.instant();
        var account = accounts.findByEmail(Account.normalizeEmail(rawEmail))
                .orElseThrow(SessionService::invalidCode);
        var challenge = challenges
                .findFirstByAccountIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(account.getId(), now)
                .orElseThrow(SessionService::invalidCode);

        if (challenge.attemptsExhausted(MAX_CHALLENGE_ATTEMPTS)) {
            throw new ApiException(ApiErrorCode.RATE_LIMITED, "error.tooManyCodeAttempts");
        }
        if (!challenge.matches(challengeHash(account.getId(), code))) {
            challenge.recordFailedAttempt();
            log.info("Hatalı ikinci faktör kodu: hesap {}", account.getId());
            throw invalidCode();
        }

        challenge.markUsed(now);
        log.info("Oturum açıldı (ikinci faktör): hesap {}", account.getId());
        return issueTokens(account, UUID.randomUUID(), now);
    }

    /**
     * Rotasyonlu yenileme (D1): sunulan token tüketilir, aynı ailede yenisi
     * verilir. Erişim token'ı hesabın <em>güncel</em> rolleriyle üretilir.
     *
     * <p>Zaten kullanılmış bir token tolerans penceresi içinde gelirse aynı
     * ailede yeni bir çift verilir: iki sekmenin eşzamanlı yenilemesi sızıntı
     * değildir (ADR-069). Pencere dışında gelirse bu bir sızıntı işaretidir:
     * ailenin tamamı iptal edilir ve iptal, istisnaya rağmen kalıcıdır.
     *
     * @throws ApiException {@code AUTH_TOKEN_INVALID}
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Authenticated refresh(String plainRefreshToken) {
        var now = clock.instant();
        var token = refreshTokens.findByTokenHash(secureTokens.hash(plainRefreshToken))
                .orElseThrow(SessionService::invalidRefresh);

        if (token.isRevoked()) {
            throw invalidRefresh();
        }
        if (token.isUsed() && token.usedWithin(REUSE_GRACE, now) && !token.isExpired(now)) {
            log.info("Yenileme token'ı tolerans penceresinde tekrar sunuldu: hesap {}", token.getAccountId());
            var account = accounts.findById(token.getAccountId()).orElseThrow(SessionService::invalidRefresh);
            return issueTokens(account, token.getFamilyId(), now);
        }
        if (token.isUsed()) {
            int revoked = refreshTokens.revokeFamily(token.getFamilyId(), now);
            log.warn("Kullanılmış yenileme token'ı tekrar sunuldu; aile iptal edildi: hesap {}, {} token",
                    token.getAccountId(), revoked);
            throw invalidRefresh();
        }
        if (token.isExpired(now)) {
            throw invalidRefresh();
        }

        token.markUsed(now);
        // Yabancı anahtar hesabı silinen token'ı da siler; yine de güvenli tarafta kalıyoruz.
        var account = accounts.findById(token.getAccountId()).orElseThrow(SessionService::invalidRefresh);
        return issueTokens(account, token.getFamilyId(), now);
    }

    /**
     * Çıkış: token'ın ailesinin tamamı iptal edilir, sonraki yenilemeler
     * reddedilir. Bilinmeyen veya zaten iptal edilmiş token sessizce kabul
     * edilir: çıkış idempotenttir ve token hakkında bilgi vermez.
     */
    @Transactional
    public void logout(String plainRefreshToken) {
        refreshTokens.findByTokenHash(secureTokens.hash(plainRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
                    log.info("Oturum kapatıldı: hesap {}", token.getAccountId());
                });
    }

    /**
     * Erişim + yenileme token'ı. Girişte yeni bir aile, yenilemede mevcut aile
     * verilir (D1).
     */
    private Authenticated issueTokens(Account account, UUID familyId, Instant now) {
        var access = accessTokens.issue(
                account.getId(),
                account.getEmail(),
                account.getRoles().stream().map(AccountRole::name).sorted().toList());

        var plainRefresh = secureTokens.newToken();
        var refreshExpiresAt = now.plus(REFRESH_TOKEN_TTL);
        refreshTokens.save(RefreshToken.issue(
                account.getId(), familyId, secureTokens.hash(plainRefresh), now, refreshExpiresAt));

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
        mailSender.send(account.getEmail(),
                messages.get("mail.loginCode.subject", account.getLocale()),
                messages.get("mail.loginCode.body", account.getLocale(), code));
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

    private static ApiException invalidCode() {
        return new ApiException(ApiErrorCode.AUTH_CODE_INVALID, "error.invalidCode");
    }

    private static ApiException invalidRefresh() {
        return new ApiException(ApiErrorCode.AUTH_TOKEN_INVALID, "error.sessionExpired");
    }
}
