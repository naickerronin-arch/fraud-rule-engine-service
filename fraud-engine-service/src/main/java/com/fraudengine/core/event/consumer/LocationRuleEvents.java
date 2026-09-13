package com.fraudengine.core.event.consumer;

import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.event.handler.RuleHandler;
import com.fraudengine.core.rule.strategy.LocationRule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(id = "fraud-location-rule", groupId = "fraud-location-rule",
        topics = "${environment}-transaction-created")
public class LocationRuleEvents {

    private final RuleHandler handler;
    private final LocationRule rule;

    @KafkaHandler
    public void ruleExecute(
            @Payload @Valid final TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) final int partition,
            @Header(KafkaHeaders.OFFSET) final long offset) {
        log.trace(
                "Received transaction {} for location check (topic: {}, partition: {}, offset: {})",
                event.getTransactionId(),
                topic,
                partition,
                offset);
        handler.handle(event, rule);
    }

    @KafkaHandler(isDefault = true)
    void handleDefault(
            @Payload final Object eventObject,
            @Header(KafkaHeaders.OFFSET) final long offset,
            @Header(KafkaHeaders.RECEIVED_PARTITION) final int partitionId,
            @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic) {
        log.info("Received an unexpected payload type {} on transaction-created (partition: {}, offset: {})",
                eventObject.getClass(), partitionId, offset);
    }
}
