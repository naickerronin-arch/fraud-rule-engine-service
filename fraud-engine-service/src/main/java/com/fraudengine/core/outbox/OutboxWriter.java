package com.fraudengine.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
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

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${environment}")
    private String environment;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(final FraudCheckCompleteEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("FraudCheckComplete")
                    .aggregateId(event.getTransactionId())
                    .topic(environment + "-fraud-check-complete")
                    .partitionKey(event.getAccountNumber())
                    .payload(payload)
                    .createdAt(Instant.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.trace("Published event to outbox: transactionId={}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to serialize FraudCheckCompleteEvent for outbox", e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
