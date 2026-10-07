package com.bahceden.common.i18n;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * İsteğin dili {@code Accept-Language} başlığından çözülür (design.md D3, ADR-031).
 *
 * <p>Yalnızca desteklenen diller kabul edilir; başlık yoksa ya da yalnızca
 * desteklenmeyen diller bildiriyorsa Türkçe seçilir. {@code web} BFF, seçili
 * dili bu başlıkla iletir (D2); {@code api} tarayıcının kendi başlığını değil
 * {@code web}'in seçtiği dili görür.
 *
 * <p>Bean adı {@code localeResolver} olmak zorundadır: {@code DispatcherServlet}
 * çözümleyiciyi bu adla arar.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        var resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SupportedLocale.locales());
        resolver.setDefaultLocale(SupportedLocale.DEFAULT.locale());
        return resolver;
    }
}
