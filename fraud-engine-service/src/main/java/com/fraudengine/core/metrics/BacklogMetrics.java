package com.fraudengine.core.metrics;

import com.fraudengine.core.outbox.OutboxStatus;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// database state rather than method calls, so these are gauges refreshed on a schedule instead of aspect metrics
@Component
@RequiredArgsConstructor
public class BacklogMetrics implements MeterBinder {

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;

    private final AtomicLong pendingTransactions = new AtomicLong();
    private final Map<OutboxStatus, AtomicLong> outboxEvents = new ConcurrentHashMap<>();

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder("fraud.transactions.pending", pendingTransactions, AtomicLong::get)
                .register(registry);

        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder("fraud.outbox.events", outboxCount(status), AtomicLong::get)
                    .tag("status", status.name())
                    .register(registry);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void refresh() {
        pendingTransactions.set(evaluatedTransactionRepository.countByFlaggedIsNull());

        for (OutboxStatus status : OutboxStatus.values()) {
            outboxCount(status).set(outboxEventRepository.countByStatus(status));
        }
    }

    private AtomicLong outboxCount(final OutboxStatus status) {
        return outboxEvents.computeIfAbsent(status, key -> new AtomicLong());
    }
}
