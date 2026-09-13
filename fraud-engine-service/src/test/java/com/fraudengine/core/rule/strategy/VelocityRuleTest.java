package com.fraudengine.core.rule.strategy;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityRuleTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private EvaluatedTransactionRepository repository;
    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        repository = mock(EvaluatedTransactionRepository.class);
        rule = new VelocityRule(new ApplicationProperties(), repository);
    }

    @Test
    void passesBelowTheDefaultThreshold() {
        givenTransactionsInWindow(4);

        RuleResult result = rule.evaluateRule(event());

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void addsRiskWithoutFlaggingAtTheThreshold() {
        givenTransactionsInWindow(5);

        RuleResult result = rule.evaluateRule(event());

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(50);
    }

    @Test
    void flagsAtTwiceTheThreshold() {
        givenTransactionsInWindow(10);

        RuleResult result = rule.evaluateRule(event());

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(100);
    }

    @Test
    void raisesTheThresholdForBusyAccounts() {
        when(repository.countByAccountNumber("ACC-1")).thenReturn(100L);
        when(repository.countByAccountNumberAndCreatedAtAfter("ACC-1", NOW.minus(Duration.ofDays(30)))).thenReturn(21600L);
        givenTransactionsInWindow(15);

        RuleResult result = rule.evaluateRule(event());

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(50);
    }

    private void givenTransactionsInWindow(final long count) {
        when(repository.countByAccountNumberAndCreatedAtAfter("ACC-1", NOW.minus(Duration.ofMinutes(10)))).thenReturn(count);
    }

    private static TransactionEvent event() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId("txn-1");
        event.setAccountNumber("ACC-1");
        event.setTransactionType("TRANSFER");
        event.setTimestamp(NOW);
        return event;
    }
}
