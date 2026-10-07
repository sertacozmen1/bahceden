package com.bahceden.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Erişim token'ı üretimi (ADR-008, design.md D7).
 *
 * <p>Token yalnızca yetkilendirme kararının ihtiyaç duyduğunu taşır: hesap
 * kimliği ({@code sub}), e-posta ve roller. JWT imzalı ama
 * <strong>şifreli değildir</strong>; ad, adres gibi kişisel veri buraya
 * konmaz (K10).
 *
 * <p>Hesap domain'ini tanımaz: çağıran ilkel değerleri verir. Böylece
 * {@code common} paketi {@code account} paketine bağımlı olmaz.
 */
@Component
public class AccessTokenIssuer {

    /** ADR-008: erişim token'ı 15 dakika yaşar. */
    public static final Duration TTL = Duration.ofMinutes(15);

    /** Rol iddiasının adı; {@code SecurityConfig} aynı adı okur. */
    public static final String ROLES_CLAIM = "roles";

    static final String EMAIL_CLAIM = "email";

    private final JwtEncoder encoder;
    private final Clock clock;

    AccessTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Üretilen token ve bitiş anı. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issue(long accountId, String email, Collection<String> roles) {
        var now = clock.instant();
        var expiresAt = now.plus(TTL);
        var claims = JwtClaimsSet.builder()
                .subject(Long.toString(accountId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(EMAIL_CLAIM, email)
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .build();
        var header = JwsHeader.with(JwtConfig.ALGORITHM).build();
        var value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
