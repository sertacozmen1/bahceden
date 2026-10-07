package com.bahceden.common.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Üretilen sözleşmenin kimlik bilgileri (ADR-009).
 *
 * <p>Sözleşme Spring kodundan üretilir, bu yüzden kalitesi kodun sorumluluğudur:
 * başlık, sürüm ve açıklama burada verilmezse springdoc "OpenAPI definition / v0"
 * gibi anlamsız varsayılanlar üretir ve istemciye bakan sözleşme zayıflar
 * (AGENTS.md K2).
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Bahçeden API",
        version = "v1",
        description = """
                Bahçeden, meyve-sebze üreticisini son tüketiciyle doğrudan buluşturan \
                çok satıcılı pazaryeridir. Bu sözleşme `api` ile `web` arasındaki tek \
                arayüzdür ve Spring kodundan üretilir; elle düzenlenmez."""))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "15 dakikalık JWT erişim token'ı (ADR-008); `POST /api/v1/sessions` ile alınır")
public class OpenApiConfig {

    /** Korunan uçların sözleşmede başvurduğu güvenlik şemasının adı. */
    public static final String BEARER_SCHEME = "bearerAuth";
}
