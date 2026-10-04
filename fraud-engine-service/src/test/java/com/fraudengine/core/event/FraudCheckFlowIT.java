package com.fraudengine.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.IntegrationTest;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.DltAuditLogRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.RuleType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class FraudCheckFlowIT extends IntegrationTest {

    private static final String TRANSACTION_TOPIC = "it-transaction-created";
    private static final String COMPLETE_TOPIC = "it-fraud-check-complete";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private EvaluatedTransactionRepository evaluatedTransactionRepository;
    @Autowired
    private RuleHitRepository ruleHitRepository;
    @Autowired
    private DltAuditLogRepository dltAuditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ========== transaction-created Tests ==========

    @Test
    void shouldClearACleanTransactionAndPublishTheVerdict() throws Exception {
        publish(transactionEvent("it-clear-1", "ACC-IT-CLEAR", new BigDecimal("205.00"), Instant.now()));

        EvaluatedTransaction stored = awaitVerdict("it-clear-1");
        assertThat(stored.getFlagged()).isFalse();
        assertThat(ruleHitRepository.findByTransactionId("it-clear-1")).hasSize(3);

        JsonNode verdict = awaitCompleteEvent("it-clear-1");
        assertThat(verdict.get("flagged").asBoolean()).isFalse();
        assertThat(verdict.get("accountNumber").asText()).isEqualTo("ACC-IT-CLEAR");
        assertThat(verdict.get("ruleEvaluations")).hasSize(3);
    }

    @Test
    void shouldFlagAnAccountThatBurstsThroughTheVelocityWindow() throws Exception {
        Instant now = Instant.now();
        for (int i = 1; i <= 11; i++) {
            insertHistory("it-history-" + i, "ACC-IT-BURST", now.minusSeconds(30L * i));
        }

        publish(transactionEvent("it-burst-1", "ACC-IT-BURST", new BigDecimal("150.00"), now));

        EvaluatedTransaction stored = awaitVerdict("it-burst-1");
        assertThat(stored.getFlagged()).isTrue();
        assertThat(ruleHitRepository.findByTransactionId("it-burst-1"))
                .filteredOn(hit -> hit.getRuleType() == RuleType.VELOCITY)
                .extracting(RuleHit::isFlagged)
                .containsExactly(true);

        JsonNode verdict = awaitCompleteEvent("it-burst-1");
        assertThat(verdict.get("flagged").asBoolean()).isTrue();
    }

    @Test
    void shouldSendAnInvalidEventToTheDeadLetterTopic() {
        publish(transactionEvent("it-invalid-1", "ACC-IT-INVALID", new BigDecimal("-500.00"), Instant.now()));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(dltAuditLogRepository.findAll()).anySatisfy(entry -> {
                    assertThat(entry.getRawPayload()).contains("it-invalid-1");
                    assertThat(entry.getOriginalTopic()).isEqualTo(TRANSACTION_TOPIC);
                }));

        assertThat(evaluatedTransactionRepository.findById("it-invalid-1")).isEmpty();
    }

    // flagged stays null until every enabled rule has reported and the verdict is written
    private EvaluatedTransaction awaitVerdict(final String transactionId) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(evaluatedTransactionRepository.findById(transactionId))
                        .get()
                        .extracting(EvaluatedTransaction::getFlagged)
                        .isNotNull());
        return evaluatedTransactionRepository.findById(transactionId).orElseThrow();
    }

    private void publish(final TransactionEvent event) {
        kafkaTemplate.send(TRANSACTION_TOPIC, event.getAccountNumber(), event);
    }

    private TransactionEvent transactionEvent(final String transactionId, final String accountNumber,
                                              final BigDecimal amount, final Instant timestamp) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(transactionId);
        event.setAccountNumber(accountNumber);
        event.setAmount(amount);
        event.setTimestamp(timestamp);
        event.setTransactionType("TRANSFER");
        event.setAreaCode("2196");
        return event;
    }

    // event_time is a plain timestamp, so write it in UTC like Hibernate does
    private void insertHistory(final String transactionId, final String accountNumber, final Instant eventTime) {
        jdbcTemplate.update("""
                INSERT INTO evaluated_transactions
                    (id, account_id, amount, transaction_type, area_code, event_time, created_at, flagged)
                VALUES (?, ?, 100.00, 'TRANSFER', '2196', ?, now(), false)
                """, transactionId, accountNumber, LocalDateTime.ofInstant(eventTime, ZoneOffset.UTC));
    }

    private JsonNode awaitCompleteEvent(final String transactionId) throws Exception {
        try (Consumer<String, String> consumer = completeEventConsumer()) {
            List<String> received = new ArrayList<>();
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofSeconds(1)).forEach(record -> received.add(record.value()));
                Optional<String> match = received.stream().filter(value -> value.contains(transactionId)).findFirst();
                if (match.isPresent()) {
                    return objectMapper.readTree(match.get());
                }
            }
        }
        throw new AssertionError("no verdict published for " + transactionId);
    }

    private Consumer<String, String> completeEventConsumer() {
        Consumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-verdict-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(COMPLETE_TOPIC));
        return consumer;
    }
}
