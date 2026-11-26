package com.bahceden.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Yayınlanmayı bekleyen ya da yayınlanmış bir iş olayı (ADR-110).
 *
 * <p>Satır, olayı doğuran veriyle aynı transaction'da yazılır; relay onu
 * Kafka'ya gönderince {@code publishedAt} dolar. Gövde, gönderilecek JSON'un
 * kendisidir ve kişisel veri taşımaz (ADR-111).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 128, updatable = false)
    private String topic;

    @Column(name = "aggregate_key", nullable = false, length = 64, updatable = false)
    private String aggregateKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutboxEvent() {
    }

    static OutboxEvent pending(UUID eventId, String eventType, String topic, String aggregateKey, String payload,
                               Instant occurredAt, Instant now) {
        var event = new OutboxEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.topic = topic;
        event.aggregateKey = aggregateKey;
        event.payload = payload;
        event.occurredAt = occurredAt;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    void markPublished(Instant now) {
        publishedAt = now;
        updatedAt = now;
    }

    void recordFailedAttempt(Instant now) {
        attempts++;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<Instant> getPublishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public int getAttempts() {
        return attempts;
    }
}
