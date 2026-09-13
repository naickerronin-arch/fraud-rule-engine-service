package com.fraudengine.core.persistence.entity;

import com.fraudengine.core.outbox.OutboxStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String topic;
    private String partitionKey;
    private String payload;
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;
    private Integer attemptCount;
    private String lastError;
    private Instant nextRetry;
    private Instant createdAt;
    private Instant publishedAt;
}
