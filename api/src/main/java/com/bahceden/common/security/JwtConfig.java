package com.bahceden.common.security;

import com.bahceden.common.BahcedenProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Erişim token'ı imzalama ve doğrulama (ADR-008).
 *
 * <p>Üçüncü parti JWT kütüphanesi kullanılmaz: {@code
 * spring-boot-starter-oauth2-resource-server} içindeki Nimbus yeterlidir
 * (ADR-002). İmza simetriktir (HS256): token'ı üreten ve doğrulayan aynı
 * uygulamadır, açık anahtar dağıtmaya gerek yoktur.
 *
 * <p>Süre kontrolü enjekte edilen {@link Clock} ile yapılır (AGENTS.md §11.3):
 * "token'ın süresi doldu" davranışı testte sabitlenebilir.
 */
@Configuration
public class JwtConfig {

    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    @Bean
    JwtEncoder jwtEncoder(BahcedenProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(BahcedenProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(ALGORITHM)
                .build();
        var timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps));
        return decoder;
    }

    private static SecretKey secretKey(BahcedenProperties properties) {
        return new SecretKeySpec(properties.jwt().keyBytes(), "HmacSHA256");
    }
}
