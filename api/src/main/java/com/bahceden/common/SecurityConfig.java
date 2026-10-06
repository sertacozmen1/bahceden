package com.bahceden.common;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP güvenlik yapılandırması.
 *
 * <p>Varsayılan <strong>deny</strong>: her uç nokta açıkça yetkilendirilir, geri
 * kalan her şey reddedilir (AGENTS.md §11.6). Bu, yeni bir controller eklendiğinde
 * yetkilendirmeyi unutmanın sonucunu "açık uç nokta" değil "403" yapar.
 *
 * <p>Oturum <strong>durumsuzdur</strong>: kimlik {@code Authorization: Bearer} ile
 * taşınır (ADR-008). {@code web} tarafındaki {@code HttpOnly} çerezler onun kendi
 * taşıma detayıdır; bu uygulama çerez görmez. Bu yüzden CSRF koruması kapalıdır:
 * CSRF, tarayıcının isteğe otomatik eklediği kimlik bilgisine karşı korur, Bearer
 * başlığı otomatik eklenmez.
 *
 * <p>Kimlik doğrulama akışının kendisi (kayıt, giriş, yenileme) bu dilimde
 * uygulanmadı; vitrin uçları herkese açıktır ve bu açıklık burada <em>açıkça</em>
 * işaretlidir.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // Yapılandırmayı tip ile enjekte etmiyoruz: Spring MVC'nin
                // HandlerMappingIntrospector'ı da CorsConfigurationSource
                // implement ediyor ve iki aday bean çakışıyor. Varsayılan
                // davranış "corsConfigurationSource" adlı bean'i bulur.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Vitrin: alıcı giriş yapmadan ürünleri görebilir (storefront-catalog spec'i).
                        .requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll()
                        // Operasyon: konteyner sağlık kontrolü ve sözleşme çıktısı.
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    /**
     * CORS yalnızca bilinen {@code web} kaynağına açılır; joker karakter yasaktır
     * (AGENTS.md §11.6). Kaynak ortam değişkeninden gelir.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(BahcedenProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.webOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
