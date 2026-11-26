package com.bahceden.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Bütün Kafka tüketicileri için tek hata işleyici (ADR-112).
 *
 * <p>Olay işlenemezse aynı kayıt kısa aralıkla yeniden denenir; denemeler
 * biterse kayıt aynı bölüm numarasıyla {@code <topic>.dlt} topic'ine yazılır ve
 * offset ilerler: tek bir bozuk olay arkadakileri sonsuza kadar bekletmez.
 * Okunamayan gövde ({@link JsonProcessingException}) yeniden denenmez; deneme
 * sonucu değiştirmez. Spring Boot bu bean'i varsayılan dinleyici fabrikasına bağlar.
 *
 * <p>Her {@code .dlt} topic'i, kaynak topic ile aynı bölüm sayısıyla, kaynak
 * topic'in tanımlandığı yerde tanımlanır (broker'da otomatik topic açılışı kapalı).
 */
@Configuration
class KafkaConsumerErrorConfig {

    /** Ölü mektup topic'lerinin son eki. */
    static final String DEAD_LETTER_SUFFIX = ".dlt";

    /** İlk denemeden sonra yeniden deneme sayısı: toplam üç deneme. */
    private static final long RETRIES = 2;

    /** İki deneme arasındaki bekleme (ms). */
    private static final long RETRY_INTERVAL_MS = 1_000;

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
