package com.bahceden.common.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

/** Outbox satırları (ADR-110). */
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Yayınlanmamış satırlar, en eskisi önce, kilitlenerek.
     *
     * <p>Kilit zaman aşımı {@code -2} Hibernate'te {@code SKIP LOCKED} demektir
     * (PostgreSQL: {@code FOR UPDATE SKIP LOCKED}): iki relay örneği aynı anda
     * çalışırsa biri diğerinin kilitlediği satırları atlar; aynı olay iki kez
     * gönderilmez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id")
    List<OutboxEvent> lockUnpublished(Limit limit);

    /** Saklama süresi geçmiş yayınlanmış satırlar; yayınlanmamış satıra dokunmaz. */
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :cutoff")
    int deletePublishedBefore(Instant cutoff);

    /** Bir olay anahtarının satırları (test ve destek için). */
    List<OutboxEvent> findByAggregateKeyOrderByIdAsc(String aggregateKey);
}
