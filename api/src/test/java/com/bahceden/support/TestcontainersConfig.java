package com.bahceden.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Entegrasyon testleri için gerçek PostgreSQL konteyneri.
 *
 * <p>H2 kullanılmaz: motor farkı (tip davranışı, kilitleme, kısmi indeks) testi
 * yalancı yeşile çevirir ve para/stok davranışını yanlış doğrular (ADR-005).
 * İmaj sürümü {@code docker-compose.yml} ile aynı tutulur: test ettiğimiz motor
 * geliştirmede çalışan motordur.
 *
 * <p>Konteyner {@code static} ve {@code reuse} olmadan tanımlandı; Spring test
 * bağlam önbelleği sayesinde aynı yapılandırmayı kullanan tüm test sınıfları
 * <strong>aynı</strong> konteyneri paylaşır. Her sınıf için yeni veritabanı
 * kaldırmak CI süresini gereksiz büyütürdü.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGE);
    }
}
