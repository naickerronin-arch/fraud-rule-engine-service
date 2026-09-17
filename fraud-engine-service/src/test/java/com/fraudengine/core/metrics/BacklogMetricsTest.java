package com.fraudengine.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fraudengine.core.outbox.OutboxStatus;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BacklogMetricsTest {

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private SimpleMeterRegistry meterRegistry;

    private BacklogMetrics backlogMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        backlogMetrics = new BacklogMetrics(evaluatedTransactionRepository, outboxEventRepository);
        backlogMetrics.bindTo(meterRegistry);
    }

    // ========== bindTo() Tests ==========

    // the gauges exist from startup, so a dashboard shows 0 rather than "no data"
    @Test
    void shouldRegisterEveryGaugeAtZero_whenBoundBeforeTheFirstRefresh() {
        assertThat(gauge("fraud.transactions.pending")).isZero();
        assertThat(outboxGauge(OutboxStatus.PENDING)).isZero();
        assertThat(outboxGauge(OutboxStatus.PUBLISHED)).isZero();
        assertThat(outboxGauge(OutboxStatus.FAILED)).isZero();
    }

    // ========== refresh() Tests ==========

    @Test
    void shouldReportTheDatabaseCounts_whenRefreshed() {
        when(evaluatedTransactionRepository.countByFlaggedIsNull()).thenReturn(3L);
        when(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(2L);
        when(outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(40L);
        when(outboxEventRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L);

        backlogMetrics.refresh();

        assertThat(gauge("fraud.transactions.pending")).isEqualTo(3.0);
        assertThat(outboxGauge(OutboxStatus.PENDING)).isEqualTo(2.0);
        assertThat(outboxGauge(OutboxStatus.PUBLISHED)).isEqualTo(40.0);
        assertThat(outboxGauge(OutboxStatus.FAILED)).isEqualTo(1.0);
    }

    // ========== Helper Methods ==========

    private double gauge(final String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private double outboxGauge(final OutboxStatus status) {
        return meterRegistry.get("fraud.outbox.events").tag("status", status.name()).gauge().value();
    }
}
