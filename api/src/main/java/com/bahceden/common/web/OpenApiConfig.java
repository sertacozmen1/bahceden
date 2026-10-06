package com.bahceden.common.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
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
public class OpenApiConfig {
}
