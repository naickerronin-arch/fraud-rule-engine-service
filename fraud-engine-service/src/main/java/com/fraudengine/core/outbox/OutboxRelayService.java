package com.fraudengine.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.config.OutboxProperties;
import com.fraudengine.core.persistence.entity.DeadLetterEntry;
import com.fraudengine.core.persistence.entity.OutboxEvent;
import com.fraudengine.core.persistence.repository.DeadLetterRepository;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:100}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.findBatchForPublishing(10);
        batch.forEach(event -> {
            try {
                sendToKafka(event);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.trace("Successfully relayed event: id={}, topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                handleFailure(event, e);
            }
        });
    }

    private void sendToKafka(final OutboxEvent event) throws Exception {
        Object payload = objectMapper.readValue(event.getPayload(), Object.class);

        var future = kafkaTemplate.send(
                MessageBuilder.withPayload(payload)
                        .setHeader(KafkaHeaders.TOPIC, event.getTopic())
                        .setHeader(KafkaHeaders.KEY, event.getPartitionKey())
                        .build());

        future.get(outboxProperties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private void handleFailure(final OutboxEvent event, final Exception e) {
        log.warn("Failed to relay event: id={}, topic={}", event.getId(), event.getTopic(), e);

        int currentAttempt = event.getAttemptCount() != null ? event.getAttemptCount() : 0;
        int nextAttempt = currentAttempt + 1;

        if (nextAttempt >= outboxProperties.getMaxAttempts()) {
            moveToDeadLetter(event, e, nextAttempt);
        } else {
            backoffEvent(event, e, nextAttempt);
        }
    }

    private void moveToDeadLetter(final OutboxEvent event, final Exception e, final int attemptCount) {
        DeadLetterEntry deadLetter = DeadLetterEntry.builder()
                .eventType(event.getAggregateType())
                .topic(event.getTopic())
                .partitionKey(event.getPartitionKey())
                .payload(event.getPayload())
                .lastError(e.getMessage())
                .attemptCount(attemptCount)
                .createdAt(event.getCreatedAt())
                .movedAt(Instant.now())
                .build();

        deadLetterRepository.save(deadLetter);

        event.setAttemptCount(attemptCount);
        event.setStatus(OutboxStatus.FAILED);
        event.setLastError(e.getMessage());
        outboxEventRepository.save(event);

        log.warn("Exhausted max attempts ({}) for event id={}, moved to dead letter",
                outboxProperties.getMaxAttempts(), event.getId());
    }

    private void backoffEvent(final OutboxEvent event, final Exception e, final int nextAttempt) {
        long backoffSeconds = Math.min(nextAttempt, 30L);
        event.setAttemptCount(nextAttempt);
        event.setLastError(e.getMessage());
        event.setNextRetry(Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS));
        outboxEventRepository.save(event);
    }
}
