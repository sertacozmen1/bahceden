package com.bahceden.common.outbox;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Tüketicilerin işlediği olaylar (ADR-112). */
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    @Modifying
    @Query("delete from ProcessedEvent e where e.processedAt < :cutoff")
    int deleteProcessedBefore(Instant cutoff);
}
