package com.bahceden;

import com.bahceden.common.BahcedenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Bahçeden REST API giriş noktası.
 *
 * <p>Bu uygulama sistemin tek yazma otoritesidir (ADR-001): doğrulama, iş kuralı,
 * yetkilendirme ve para hesabı burada yaşar. {@code web} yalnızca HTTP ile buraya
 * konuşur; iki birim birbirini import etmez.
 */
@SpringBootApplication
@EnableConfigurationProperties(BahcedenProperties.class)
public class BahcedenApplication {

    public static void main(String[] args) {
        SpringApplication.run(BahcedenApplication.class, args);
    }
}
