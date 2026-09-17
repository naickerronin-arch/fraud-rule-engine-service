package com.fraudengine.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.config.OutboxProperties;
import com.fraudengine.core.persistence.entity.DeadLetterEntry;
import com.fraudengine.core.persistence.entity.OutboxEvent;
import com.fraudengine.core.persistence.repository.DeadLetterRepository;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    private static final String TOPIC = "local-fraud-check-complete";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private DeadLetterRepository deadLetterRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        service = new OutboxRelayService(
                outboxEventRepository, deadLetterRepository, kafkaTemplate, new OutboxProperties(), new ObjectMapper());
    }

    // ========== relay() Tests ==========

    @Test
    void shouldMarkTheEventPublished_whenTheBrokerAcknowledgesIt() {
        OutboxEvent event = givenPendingEvent(null);
        givenTheBrokerAccepts();

        service.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
        verifyNoInteractions(deadLetterRepository);
    }

    @Test
    void shouldBackOffAndRetryLater_whenSendingFails() {
        OutboxEvent event = givenPendingEvent(null);
        givenTheBrokerRejects();

        service.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
        assertThat(event.getNextRetry()).isAfter(Instant.now().minusSeconds(1));
        verify(outboxEventRepository).save(event);
        verifyNoInteractions(deadLetterRepository);
    }

    // rows are kept as an audit trail, so the copy in dead_letter is the operator's queue
    @Test
    void shouldFailTheEventAndDeadLetterIt_whenTheAttemptsRunOut() {
        OutboxEvent event = givenPendingEvent(4);
        givenTheBrokerRejects();

        service.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttemptCount()).isEqualTo(5);
        verify(outboxEventRepository).save(event);

        ArgumentCaptor<DeadLetterEntry> captor = ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(deadLetterRepository).save(captor.capture());

        DeadLetterEntry deadLetter = captor.getValue();
        assertThat(deadLetter.getAttemptCount()).isEqualTo(5);
        assertThat(deadLetter.getTopic()).isEqualTo(TOPIC);
        assertThat(deadLetter.getPayload()).isEqualTo(event.getPayload());
        assertThat(deadLetter.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    // ========== Helper Methods ==========

    private OutboxEvent givenPendingEvent(final Integer attemptCount) {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .aggregateType("FraudCheckComplete")
                .aggregateId("txn-1")
                .topic(TOPIC)
                .partitionKey("ACC-1")
                .payload("{\"transactionId\":\"txn-1\"}")
                .status(OutboxStatus.PENDING)
                .attemptCount(attemptCount)
                .createdAt(CREATED_AT)
                .build();
        when(outboxEventRepository.findBatchForPublishing(10)).thenReturn(List.of(event));
        return event;
    }

    private void givenTheBrokerAccepts() {
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    private void givenTheBrokerRejects() {
        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
    }
}
