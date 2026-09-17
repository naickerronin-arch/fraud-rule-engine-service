package com.fraudengine.core.rule.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    private static final String ACCOUNT_NUMBER = "ACC-1";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofMinutes(10));
    private static final Instant BASELINE_START = NOW.minus(Duration.ofDays(30));

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    private VelocityRule rule;

    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        rule = new VelocityRule(new ApplicationProperties(), evaluatedTransactionRepository);

        testEvent = new TransactionEvent();
        testEvent.setTransactionId("txn-1");
        testEvent.setAccountNumber(ACCOUNT_NUMBER);
        testEvent.setTransactionType("TRANSFER");
        testEvent.setTimestamp(NOW);
    }

    // ========== evaluateRule() Tests ==========

    @Test
    void shouldPass_whenCountIsBelowTheDefaultThreshold() {
        givenTransactionsInWindow(4);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.status()).isEqualTo(RuleHitStatus.EVALUATED);
        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void shouldAddRiskWithoutFlagging_whenCountReachesTheThreshold() {
        givenTransactionsInWindow(5);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(50);
    }

    @Test
    void shouldAddRiskWithoutFlagging_whenCountIsJustUnderTwiceTheThreshold() {
        givenTransactionsInWindow(9);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(90);
    }

    @Test
    void shouldFlag_whenCountReachesTwiceTheThreshold() {
        givenTransactionsInWindow(10);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(100);
    }

    @Test
    void shouldCapRiskAtOneHundred_whenCountIsFarAboveTheThreshold() {
        givenTransactionsInWindow(200);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(100);
    }

    // ========== fetchBaseLine() Tests ==========

    @Test
    void shouldRaiseTheThreshold_whenTheAccountHasEnoughHistoryToBeBusy() {
        when(evaluatedTransactionRepository.countByAccountNumber(ACCOUNT_NUMBER)).thenReturn(100L);
        when(evaluatedTransactionRepository.countByAccountNumberAndCreatedAtAfter(ACCOUNT_NUMBER, BASELINE_START))
                .thenReturn(21600L);
        givenTransactionsInWindow(15);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(50);
    }

    // ========== Helper Methods ==========

    private void givenTransactionsInWindow(final long count) {
        when(evaluatedTransactionRepository.countByAccountNumberAndCreatedAtAfter(ACCOUNT_NUMBER, WINDOW_START))
                .thenReturn(count);
    }
}
