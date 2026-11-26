package com.bahceden.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox'a yazılabilen bir olay gövdesi (ADR-110, ADR-111).
 *
 * <p>Uygulayan tip bir {@code record}'dur ve JSON'a bileşenleriyle yazılır. Bu
 * arayüzün yalnızca {@link #eventId()}, {@link #eventType()} ve
 * {@link #occurredAt()} metotları gövdede görünür (record bileşenleri oldukları
 * için); {@link #topic()} ve {@link #aggregateKey()} yönlendirme bilgisidir.
 */
public interface OutboxMessage {

    /** Tüketicinin tekrar teslimi tanıdığı kimlik. */
    UUID eventId();

    /** {@code SCREAMING_SNAKE_CASE} olay adı ({@code ORDER_PAID}). */
    String eventType();

    Instant occurredAt();

    /** Sürümlü topic adı ({@code bahceden.order.paid.v1}). */
    String topic();

    /** Kafka anahtarı: aynı anahtarın olayları aynı bölümde, sıralı. */
    String aggregateKey();
}
