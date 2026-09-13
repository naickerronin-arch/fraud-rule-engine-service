package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.outbox.OutboxStatus;
import com.fraudengine.core.persistence.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(
            value = """
                SELECT * FROM outbox_events
                WHERE status = 'PENDING'
                  AND (next_retry IS NULL OR next_retry <= now())
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """,
            nativeQuery = true)
    List<OutboxEvent> findBatchForPublishing(@Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :publishedAt WHERE o.id = :id")
    void markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);

    long countByStatus(OutboxStatus status);
}
