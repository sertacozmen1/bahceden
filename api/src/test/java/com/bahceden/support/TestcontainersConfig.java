package com.bahceden.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Entegrasyon testleri için gerçek PostgreSQL, Redis ve Kafka konteynerleri.
 *
 * <p>H2 kullanılmaz: motor farkı (tip davranışı, kilitleme, kısmi indeks) testi
 * yalancı yeşile çevirir ve para/stok davranışını yanlış doğrular (ADR-005).
 * Aynı gerekçeyle Redis ve Kafka da taklit edilmez ve gömülü broker kullanılmaz:
 * Lua betiği, TTL ve offset davranışı ancak gerçek serviste doğrulanır (ADR-107).
 * İmaj sürümleri {@code docker-compose.yml} ile aynı tutulur: test ettiğimiz
 * servis geliştirmede çalışan servistir.
 *
 * <p>Konteynerler {@code static} ve {@code reuse} olmadan tanımlandı; Spring test
 * bağlam önbelleği sayesinde aynı yapılandırmayı kullanan tüm test sınıfları
 * <strong>aynı</strong> konteynerleri paylaşır. Her sınıf için yeni servis
 * kaldırmak CI süresini gereksiz büyütürdü.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final DockerImageName POSTGRES = DockerImageName.parse("postgres:17-alpine");
    private static final DockerImageName REDIS = DockerImageName.parse("redis:7.2-alpine");
    private static final DockerImageName KAFKA = DockerImageName.parse("apache/kafka:3.9.1");
    private static final int REDIS_PORT = 6379;

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS).withExposedPorts(REDIS_PORT);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA);
    }
}
