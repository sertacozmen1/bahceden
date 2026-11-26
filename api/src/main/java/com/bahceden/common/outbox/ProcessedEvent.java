package com.bahceden.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Bir tüketicinin işlediği olay (ADR-112): tekrar teslimi ayıklayan kayıt.
 *
 * <p>Birincil anahtar (tüketici, olay kimliği) korumanın kendisidir: aynı olay
 * aynı anda iki kez işlenmeye çalışılırsa ikinci transaction anahtar
 * çakışmasıyla geri alınır ve yeniden denendiğinde olayı işlenmiş görür.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    /** Bileşik anahtar. */
    @Embeddable
    public record Key(@Column(nullable = false, length = 64) String consumer,
                      @Column(name = "event_id", nullable = false) UUID eventId) implements Serializable {
    }

    @EmbeddedId
    private Key key;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessedEvent() {
    }

    static ProcessedEvent of(Key key, Instant now) {
        var event = new ProcessedEvent();
        event.key = key;
        event.processedAt = now;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }
}
