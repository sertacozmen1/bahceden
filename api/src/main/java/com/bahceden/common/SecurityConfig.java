package com.bahceden.common;

import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.common.security.ApiAccessDeniedHandler;
import com.bahceden.common.security.ApiAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP güvenlik yapılandırması.
 *
 * <p>Varsayılan <strong>deny</strong>: her uç nokta açıkça yetkilendirilir, geri
 * kalan her şey reddedilir (AGENTS.md §11.6). Bu, yeni bir controller eklendiğinde
 * yetkilendirmeyi unutmanın sonucunu "açık uç nokta" değil "reddedilen istek" yapar.
 *
 * <p>Oturum <strong>durumsuzdur</strong>: kimlik {@code Authorization: Bearer} ile
 * taşınır (ADR-008). {@code web} tarafındaki {@code HttpOnly} çerezler onun kendi
 * taşıma detayıdır; bu uygulama çerez görmez. Bu yüzden CSRF koruması kapalıdır:
 * CSRF, tarayıcının isteğe otomatik eklediği kimlik bilgisine karşı korur, Bearer
 * başlığı otomatik eklenmez.
 *
 * <p>Erişim token'ı JWT'dir ve bu uygulama onu resource server olarak doğrular
 * (ADR-008). Yetkilendirme kararı token'daki {@code roles} iddiasıyla verilir,
 * her istekte veritabanına gidilmez (design.md D7). Herkese açık uçlar (vitrin,
 * kayıt, doğrulama, giriş) burada <em>açıkça</em> işaretlidir.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ApiAuthenticationEntryPoint entryPoint,
                                    ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
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
                        // Ürün detayı ve fotoğraflar herkese açık; görünürlük sorguda (add-product-listings D5, D6).
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*", "/api/v1/product-photos/*").permitAll()
                        // Kimlik: hesap açma, doğrulama ve giriş oturumdan önce gelir, doğal olarak açıktır.
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/verifications").permitAll()
                        // Parola sıfırlama: parolasını unutan kullanıcının oturumu yoktur; kanıt e-postadaki token'dır.
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/password-resets",
                                "/api/v1/accounts/password-resets/confirmations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions").permitAll()
                        // İkinci adım, yenileme ve çıkış: kimlik kanıtı gövdedeki kod veya yenileme token'ıdır.
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/challenge", "/api/v1/sessions/refresh").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/current").permitAll()
                        // Oturum sahibi: geçerli erişim token'ı ister; rol fark etmez.
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/me/verification-requests").authenticated()
                        // Satıcı başvurusu: başvuran henüz satıcı değildir; oturum yeter, rol aranmaz (ADR-037).
                        .requestMatchers(HttpMethod.GET, "/api/v1/sellers/me", "/api/v1/sellers/me/document").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sellers/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sellers/me").authenticated()
                        // Satıcı ilanları: rol token'dan; otorite serviste denetlenen satıcı durumudur (ADR-044).
                        .requestMatchers("/api/v1/sellers/me/products", "/api/v1/sellers/me/products/**").hasRole("SELLER")
                        // Admin: satıcı başvurularını karara bağlama (add-seller-onboarding D7). Rol token'dan gelir.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/sellers", "/api/v1/admin/sellers/*",
                                "/api/v1/admin/sellers/*/document").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/products/*/remove",
                                "/api/v1/admin/products/*/reinstate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/sellers/*/approve", "/api/v1/admin/sellers/*/reject",
                                "/api/v1/admin/sellers/*/suspend", "/api/v1/admin/sellers/*/reinstate").hasRole("ADMIN")
                        // Operasyon: konteyner sağlık kontrolü ve sözleşme çıktısı.
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken())))
                // Kimliksiz istek (token hiç yok) da aynı 401 gövdesini alır.
                // Rolü yetmeyen istek (403) de ortak hata gövdesini alır.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * Token'daki {@code roles} iddiası doğrudan yetki olur. Roller zaten
     * {@code ROLE_} önekiyle saklandığı için (AccountRole) ikinci önek eklenmez.
     */
    private static JwtAuthenticationConverter rolesFromToken() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessTokenIssuer.ROLES_CLAIM);
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
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
