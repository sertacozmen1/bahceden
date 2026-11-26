package com.bahceden.common.outbox;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tüketici tarafı tekrar koruması (ADR-112).
 *
 * <p>Tüketici olayı işlerken, kendi transaction'ı içinde önce bunu çağırır.
 * Kayıt işin sonuçlarıyla birlikte kalıcı olur; iş geri alınırsa kayıt da geri
 * alınır ve olay yeniden denendiğinde işlenir. Ham SQL kullanılmaz
 * (AGENTS.md §11.4): varlık kontrolü + ekleme; eşzamanlı ikinci ekleme birincil
 * anahtar çakışmasıyla düşer.
 */
@Component
public class ProcessedEvents {

    private final ProcessedEventRepository events;
    private final Clock clock;

    ProcessedEvents(ProcessedEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * Olayı bu tüketici için işlenmiş olarak kaydeder.
     *
     * @return olay ilk kez görülüyorsa {@code true}; daha önce işlenmişse {@code false}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markIfFirst(String consumer, UUID eventId) {
        var key = new ProcessedEvent.Key(consumer, eventId);
        if (events.existsById(key)) {
            return false;
        }
        events.saveAndFlush(ProcessedEvent.of(key, clock.instant()));
        return true;
    }
}
