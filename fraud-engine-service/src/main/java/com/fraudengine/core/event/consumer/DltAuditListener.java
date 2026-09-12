package com.fraudengine.core.event.consumer;

import com.fraudengine.core.persistence.entity.DltAuditLog;
import com.fraudengine.core.persistence.repository.DltAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(
        id = "fraud-dlt-audit",
        groupId = "fraud-dlt-audit",
        topics = "${environment}-transaction-created-dlt",
        properties = {
            "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
        })
public class DltAuditListener {

    private final DltAuditLogRepository dltAuditLogRepository;

    @KafkaHandler
    public void handle(
            @Payload(required = false) final String payload,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) final String originalTopic,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) final Integer originalPartition,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) final Long originalOffset,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) final String exceptionClass,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) final String exceptionMessage) {

        log.warn("Recording DLT entry — originalTopic={} originalPartition={} originalOffset={} exceptionClass={} exceptionMessage={}",
                originalTopic, originalPartition, originalOffset, exceptionClass, exceptionMessage);

        DltAuditLog entry = DltAuditLog.builder()
                .originalTopic(originalTopic)
                .originalPartition(originalPartition)
                .originalOffset(originalOffset)
                .exceptionClass(exceptionClass)
                .exceptionMessage(exceptionMessage)
                .rawPayload(payload)
                .receivedAt(Instant.now())
                .build();
        dltAuditLogRepository.save(entry);
    }
}
