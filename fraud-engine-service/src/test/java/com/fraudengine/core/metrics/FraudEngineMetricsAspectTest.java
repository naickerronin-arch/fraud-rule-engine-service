package com.fraudengine.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.event.consumer.DltAuditListener;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.DltAuditLogRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import com.fraudengine.core.service.AdminService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// the aspect only runs through a proxy, so every target here is wrapped the way Spring wraps its beans
class FraudEngineMetricsAspectTest {

    private SimpleMeterRegistry meterRegistry;

    private FraudEngineMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new FraudEngineMetricsAspect(new MetricsRecorder(meterRegistry));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ========== registerCounters() Tests ==========

    // without this, a counter's first event is also its first sample, and rate()/increase() miss it
    @Test
    void shouldCreateEveryCounterAtZero_whenTheAspectStartsUp() {
        aspect.registerCounters();

        assertThat(counter("fraud.check.complete", "flagged", "true")).isZero();
        assertThat(counter("fraud.check.complete", "flagged", "false")).isZero();
        assertThat(counter("fraud.transaction.override", "flagged", "true")).isZero();
        assertThat(meterRegistry.get("fraud.dlt.received").counter().count()).isZero();
        assertThat(meterRegistry.find("fraud.rule.evaluated").counters()).hasSize(6);
    }

    // ========== recordRuleEvaluation() Tests ==========

    @Test
    void shouldCountTheResultAndTime_whenARuleEvaluates() {
        FraudRule rule = proxy(new StubRule());

        rule.evaluateRule(new TransactionEvent());

        assertThat(counter("fraud.rule.evaluated", "rule", "VELOCITY", "status", "EVALUATED", "flagged", "true"))
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("fraud.rule.duration").tag("rule", "VELOCITY").timer().count()).isEqualTo(1);
    }

    // the failure is still counted and still rethrown, so Kafka's retry logic sees it
    @Test
    void shouldCountAnError_whenARuleThrows() {
        FraudRule rule = proxy(new FailingRule());

        assertThatThrownBy(() -> rule.evaluateRule(new TransactionEvent())).isInstanceOf(IllegalStateException.class);

        assertThat(counter("fraud.rule.evaluated", "rule", "GEO", "status", "ERROR", "flagged", "false")).isEqualTo(1.0);
        assertThat(meterRegistry.get("fraud.rule.duration").tag("rule", "GEO").timer().count()).isEqualTo(1);
    }

    // ========== recordFraudCheckComplete() Tests ==========

    // a rolled-back verdict must not be counted, so the increment waits for the commit
    @Test
    void shouldCountTheCompletion_onlyAfterTheTransactionCommits() {
        OutboxWriter outboxWriter = proxy(
                new OutboxWriter(mock(OutboxEventRepository.class), new ObjectMapper().findAndRegisterModules()));
        FraudCheckCompleteEvent event = FraudCheckCompleteEvent.builder()
                .transactionId("txn-1")
                .flagged(true)
                .completedAt(Instant.now())
                .build();
        TransactionSynchronizationManager.initSynchronization();

        outboxWriter.publish(event);

        assertThat(meterRegistry.find("fraud.check.complete").counter()).isNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(counter("fraud.check.complete", "flagged", "true")).isEqualTo(1.0);
    }

    // ========== recordOverride() Tests ==========

    @Test
    void shouldCountTheOverride_whenComplianceChangesAVerdict() {
        EvaluatedTransactionRepository repository = mock(EvaluatedTransactionRepository.class);
        when(repository.findById("txn-1")).thenReturn(Optional.of(new EvaluatedTransaction()));
        AdminService adminService = proxy(new AdminService(repository, mock(BadLocationRepository.class)));

        adminService.overrideTransaction("txn-1", new OverrideTransactionRequest(false));

        assertThat(counter("fraud.transaction.override", "flagged", "false")).isEqualTo(1.0);
    }

    // ========== recordDltReceived() Tests ==========

    @Test
    void shouldCountTheDeadLetter_whenTheAuditListenerHandlesOne() {
        DltAuditListener listener = proxy(new DltAuditListener(mock(DltAuditLogRepository.class)));

        listener.handle("{}", "local-transaction-created", 0, 1L, "SomeException", "failed");

        assertThat(meterRegistry.get("fraud.dlt.received").counter().count()).isEqualTo(1.0);
    }

    // ========== Helper Methods ==========

    private double counter(final String name, final String... tags) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }

    private <T> T proxy(final T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    static class StubRule implements FraudRule {

        @Override
        public RuleType ruleType() {
            return RuleType.VELOCITY;
        }

        @Override
        public boolean isEnabledFor(final String transactionType) {
            return true;
        }

        @Override
        public RuleResult evaluateRule(final TransactionEvent transaction) {
            return RuleResult.builder()
                    .status(RuleHitStatus.EVALUATED)
                    .flagged(true)
                    .riskLevel(100)
                    .build();
        }
    }

    static class FailingRule implements FraudRule {

        @Override
        public RuleType ruleType() {
            return RuleType.GEO;
        }

        @Override
        public boolean isEnabledFor(final String transactionType) {
            return true;
        }

        @Override
        public RuleResult evaluateRule(final TransactionEvent transaction) {
            throw new IllegalStateException("query failed");
        }
    }
}
