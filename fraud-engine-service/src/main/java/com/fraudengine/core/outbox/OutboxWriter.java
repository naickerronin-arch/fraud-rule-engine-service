package com.fraudengine.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.event.domain.FraudCheckFailedEvent;
import com.fraudengine.core.persistence.entity.OutboxEvent;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private static final String COMPLETE_TOPIC_SUFFIX = "-fraud-check-complete";
    private static final String FAILED_TOPIC_SUFFIX = "-fraud-check-failed";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${environment}")
    private String environment;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(final FraudCheckCompleteEvent event) {
        write("FraudCheckComplete", environment + COMPLETE_TOPIC_SUFFIX,
                event.getTransactionId(), event.getAccountNumber(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(final FraudCheckFailedEvent event) {
        write("FraudCheckFailed", environment + FAILED_TOPIC_SUFFIX,
                event.getTransactionId(), event.getAccountNumber(), event);
    }

    private void write(final String aggregateType, final String topic, final String aggregateId,
                       final String partitionKey, final Object event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .partitionKey(partitionKey)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.trace("Published event to outbox: aggregateType={}, transactionId={}", aggregateType, aggregateId);
        } catch (Exception e) {
            log.error("Failed to serialize {} for outbox", aggregateType, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
