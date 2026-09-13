package com.fraudengine.core.rule.strategy;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BehavioralDeviationRuleTest {

    private EvaluatedTransactionRepository repository;
    private BehavioralDeviationRule rule;

    @BeforeEach
    void setUp() {
        repository = mock(EvaluatedTransactionRepository.class);
        rule = new BehavioralDeviationRule(new ApplicationProperties(), repository);
    }

    @Test
    void comparesAgainstTheAccountsOtherTransactions() {
        when(repository.countByAccountNumber("ACC-1")).thenReturn(25L);
        when(repository.findAmountStatsByAccountNumber("ACC-1", "txn-1")).thenReturn(stats("100", "10"));

        RuleResult result = rule.evaluateRule(event("150"));

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(100);
        verify(repository, never()).findAmountStatsByTransactionType(any(), any());
    }

    @Test
    void fallsBackToTheTransactionTypeForNewAccounts() {
        when(repository.countByAccountNumber("ACC-1")).thenReturn(3L);
        when(repository.findAmountStatsByTransactionType("TRANSFER", "txn-1")).thenReturn(stats("100", "10"));

        RuleResult result = rule.evaluateRule(event("140"));

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(80);
        verify(repository, never()).findAmountStatsByAccountNumber(any(), any());
    }

    @Test
    void ignoresSmallDeviations() {
        when(repository.findAmountStatsByTransactionType("TRANSFER", "txn-1")).thenReturn(stats("100", "10"));

        RuleResult result = rule.evaluateRule(event("120"));

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void doesNotFlagWithoutASpreadInAmounts() {
        when(repository.findAmountStatsByTransactionType("TRANSFER", "txn-1"))
                .thenReturn(stats("100", "0"))
                .thenReturn(stats("100", null));

        RuleResult zeroStdDev = rule.evaluateRule(event("10000"));
        RuleResult noStdDev = rule.evaluateRule(event("10000"));

        assertThat(zeroStdDev.flagged()).isFalse();
        assertThat(noStdDev.flagged()).isFalse();
    }

    private static TransactionEvent event(final String amount) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId("txn-1");
        event.setAccountNumber("ACC-1");
        event.setTransactionType("TRANSFER");
        event.setAmount(new BigDecimal(amount));
        return event;
    }

    private static EvaluatedTransactionRepository.AmountStats stats(final String avg, final String stdDev) {
        return new EvaluatedTransactionRepository.AmountStats() {
            @Override
            public BigDecimal getAvgAmount() {
                return new BigDecimal(avg);
            }

            @Override
            public BigDecimal getStdDevAmount() {
                return stdDev == null ? null : new BigDecimal(stdDev);
            }
        };
    }
}
