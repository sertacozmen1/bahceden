package com.bahceden.account;

import com.bahceden.common.BahcedenProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Süresi geçmiş kimlik kayıtlarının temizliği (account-identity spec'i
 * "Süresi geçmiş kimlik kayıtları temizlenir", add-stale-record-cleanup D2).
 *
 * <p>Saklama payı kadar önce süresi dolmuş doğrulama ve sıfırlama token'ları
 * ile giriş kodları silinir. Yenileme token'ları aileyle silinir ve yalnızca
 * ailenin bütün token'larının süresi dolmuşsa: kullanılmış eski bir token'ın
 * tekrar sunulması aileyi iptal eder (ADR-018), canlı ailenin satırı kalmalıdır.
 *
 * <p>Her tablo kendi transaction'ında silinir; biri hata verirse diğerleri yine
 * temizlenir. Log'a yalnızca sayılar yazılır (K10).
 */
@Component
public class ExpiredTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanup.class);

    /** Bir turda silinen satır sayıları. */
    public record Result(int verificationTokens, int passwordResetTokens, int loginChallenges, int refreshTokens) {

        int total() {
            return verificationTokens + passwordResetTokens + loginChallenges + refreshTokens;
        }
    }

    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final LoginChallengeRepository loginChallenges;
    private final RefreshTokenRepository refreshTokens;
    private final TransactionTemplate transactions;
    private final Duration retention;
    private final Clock clock;

    ExpiredTokenCleanup(EmailVerificationTokenRepository verificationTokens,
                        PasswordResetTokenRepository passwordResetTokens,
                        LoginChallengeRepository loginChallenges,
                        RefreshTokenRepository refreshTokens,
                        PlatformTransactionManager transactionManager,
                        BahcedenProperties properties,
                        Clock clock) {
        this.verificationTokens = verificationTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.loginChallenges = loginChallenges;
        this.refreshTokens = refreshTokens;
        this.transactions = new TransactionTemplate(transactionManager);
        this.retention = properties.cleanup().tokenRetention();
        this.clock = clock;
    }

    /** Tek tur. */
    public Result cleanOnce() {
        var cutoff = clock.instant().minus(retention);
        var result = new Result(
                delete(() -> verificationTokens.deleteExpiredBefore(cutoff)),
                delete(() -> passwordResetTokens.deleteExpiredBefore(cutoff)),
                delete(() -> loginChallenges.deleteExpiredBefore(cutoff)),
                delete(() -> refreshTokens.deleteExpiredFamiliesBefore(cutoff)));
        if (result.total() > 0) {
            log.info("Süresi geçmiş kimlik kayıtları silindi: doğrulama {}, sıfırlama {}, giriş kodu {}, yenileme {}",
                    result.verificationTokens(), result.passwordResetTokens(), result.loginChallenges(),
                    result.refreshTokens());
        }
        return result;
    }

    private int delete(IntSupplier deletion) {
        try {
            var deleted = transactions.execute(status -> deletion.getAsInt());
            return deleted == null ? 0 : deleted;
        } catch (RuntimeException cause) {
            log.warn("Süresi geçmiş kimlik kayıtları silinemedi, sonraki turda yeniden denenecek", cause);
            return 0;
        }
    }
}
