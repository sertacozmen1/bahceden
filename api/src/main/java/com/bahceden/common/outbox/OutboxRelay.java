package com.bahceden.common.outbox;

import com.bahceden.common.BahcedenProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox'taki yayınlanmamış olayları Kafka'ya gönderir (ADR-110).
 *
 * <p>Bir tur tek transaction'dır: satırlar {@code SKIP LOCKED} ile kilitlenir,
 * kimlik sırasıyla gönderilir, her gönderim broker onayı ({@code acks=all})
 * beklenerek tamamlanır ve satır işaretlenir. Bir gönderim başarısız olursa
 * satırın deneme sayısı artar ve tur orada biter: sıra korunur, broker dönünce
 * aynı satırdan devam edilir. Teslim en az bir kezdir; tekrarı tüketici ayıklar.
 *
 * <p>Log'a olay kimliği, tipi ve topic yazılır; gövde yazılmaz (K10).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository events;
    private final ProcessedEventRepository processedEvents;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final BahcedenProperties.Outbox settings;
    private final Clock clock;

    OutboxRelay(OutboxRepository events, ProcessedEventRepository processedEvents, KafkaTemplate<String, String> kafka,
                PlatformTransactionManager transactionManager, BahcedenProperties properties, Clock clock) {
        this.events = events;
        this.processedEvents = processedEvents;
        this.kafka = kafka;
        this.transactions = new TransactionTemplate(transactionManager);
        this.settings = properties.outbox();
        this.clock = clock;
    }

    /**
     * Tek tur.
     *
     * @return bu turda yayınlanan olay sayısı
     */
    public int relayOnce() {
        Integer published = transactions.execute(status -> {
            int sent = 0;
            for (var event : events.lockUnpublished(Limit.of(settings.batchSize()))) {
                if (!send(event)) {
                    event.recordFailedAttempt(clock.instant());
                    break;
                }
                event.markPublished(clock.instant());
                sent++;
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    /**
     * Saklama süresi geçmiş yayınlanmış outbox satırlarını ve tüketicilerin
     * işlenmiş olay kayıtlarını siler; yayınlanmamış satır kalır.
     *
     * @return silinen outbox satırı sayısı
     */
    public int purgePublished() {
        var cutoff = clock.instant().minus(settings.retention());
        Integer deleted = transactions.execute(status -> events.deletePublishedBefore(cutoff));
        Integer processed = transactions.execute(status -> processedEvents.deleteProcessedBefore(cutoff));
        int count = deleted == null ? 0 : deleted;
        int processedCount = processed == null ? 0 : processed;
        if (count > 0 || processedCount > 0) {
            log.info("Saklama süresi geçmiş olay kayıtları silindi: outbox {}, işlenmiş {}", count, processedCount);
        }
        return count;
    }

    private boolean send(OutboxEvent event) {
        var timeout = settings.sendTimeout();
        try {
            kafka.send(event.getTopic(), event.getAggregateKey(), event.getPayload())
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException | KafkaException | org.springframework.kafka.KafkaException cause) {
            warn(event, cause, timeout);
            return false;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            warn(event, cause, timeout);
            return false;
        }
    }

    private static void warn(OutboxEvent event, Exception cause, Duration timeout) {
        log.warn("Olay Kafka'ya gönderilemedi, sonraki turda yeniden denenecek: olay {} ({}), topic {}, deneme {}, "
                        + "zaman aşımı {} ms: {}", event.getEventId(), event.getEventType(), event.getTopic(),
                event.getAttempts() + 1, timeout.toMillis(), cause.getClass().getSimpleName());
    }
}
