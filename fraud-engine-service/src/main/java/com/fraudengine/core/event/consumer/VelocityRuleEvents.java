package com.fraudengine.core.event.consumer;

import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.event.handler.RuleHandler;
import com.fraudengine.core.rule.strategy.VelocityRule;
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
@KafkaListener(id = "fraud-velocity-rule", groupId = "fraud-velocity-rule",
        topics = "${environment}-transaction-created")
public class VelocityRuleEvents {

    private final RuleHandler ruleHandler;
    private final VelocityRule velocityRule;

    @KafkaHandler
    public void ruleExecute(
            final TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) final int partition,
            @Header(KafkaHeaders.OFFSET) final long offset){
        log.trace(
                "Received Transaction Event: {} (topic: {}, partition: {}, offset: {})",
                event.getTransactionId(),
                topic,
                partition,
                offset);
        ruleHandler.handle(event, velocityRule);
    }

    @KafkaHandler(isDefault = true)
    void handleDefault(
            @Payload final Object eventObject,
            @Header(KafkaHeaders.OFFSET) final long offset,
            @Header(KafkaHeaders.RECEIVED_PARTITION) final int partitionId,
            @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic) {
        log.info("Server received unknown message {},{},{}", offset, partitionId, topic);
    }

}
