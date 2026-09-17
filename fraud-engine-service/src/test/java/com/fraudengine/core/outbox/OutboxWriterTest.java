package com.fraudengine.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.persistence.entity.OutboxEvent;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private FraudCheckCompleteEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = FraudCheckCompleteEvent.builder()
                .transactionId("txn-1")
                .accountNumber("ACC-1")
                .transactionType("TRANSFER")
                .flagged(true)
                .completedAt(Instant.now())
                .build();
    }

    // ========== publish() Tests ==========

    // the row is written in the caller's transaction, so it commits with the verdict or not at all
    @Test
    void shouldWriteAPendingRowForTheRelay_whenTheEventIsPublished() {
        OutboxWriter writer = writer(new ObjectMapper().findAndRegisterModules());

        writer.publish(testEvent);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAggregateType()).isEqualTo("FraudCheckComplete");
        assertThat(saved.getAggregateId()).isEqualTo("txn-1");
        assertThat(saved.getTopic()).isEqualTo("local-fraud-check-complete");
        assertThat(saved.getPartitionKey()).isEqualTo("ACC-1");
        assertThat(saved.getPayload()).contains("\"transactionId\":\"txn-1\"");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // failing here rolls the verdict back too, rather than leaving a verdict nobody hears about
    @Test
    void shouldThrow_whenTheEventCannotBeSerialized() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        OutboxWriter writer = writer(objectMapper);

        assertThatThrownBy(() -> writer.publish(testEvent)).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(outboxEventRepository);
    }

    // ========== Helper Methods ==========

    private OutboxWriter writer(final ObjectMapper objectMapper) {
        OutboxWriter writer = new OutboxWriter(outboxEventRepository, objectMapper);
        ReflectionTestUtils.setField(writer, "environment", "local");
        return writer;
    }
}
