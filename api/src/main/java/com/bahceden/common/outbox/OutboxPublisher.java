package com.bahceden.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Olayı outbox'a, çağıranın transaction'ı içinde yazar (ADR-110).
 *
 * <p>{@code MANDATORY}: açık bir transaction yoksa hata verir. Olay, onu doğuran
 * veriyle ya birlikte kalıcı olur ya hiç olmaz; Kafka'ya gönderim relay'in
 * işidir, burada ağ çağrısı yoktur.
 */
@Component
public class OutboxPublisher {

    private final OutboxRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    OutboxPublisher(OutboxRepository events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxMessage message) {
        events.save(OutboxEvent.pending(message.eventId(), message.eventType(), message.topic(),
                message.aggregateKey(), json(message), message.occurredAt(), clock.instant()));
    }

    private String json(OutboxMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException cause) {
            // Olay tipleri yalnızca sayı, metin ve kimlik taşıyan record'lardır; yazılamaması bir programlama hatasıdır.
            throw new IllegalStateException("Olay JSON'a yazılamadı: " + message.eventType(), cause);
        }
    }
}
