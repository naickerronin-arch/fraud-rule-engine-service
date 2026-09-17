package com.fraudengine.core.rule.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BehavioralDeviationRuleTest {

    private static final String ACCOUNT_NUMBER = "ACC-1";
    private static final String TRANSACTION_ID = "txn-1";
    private static final String TRANSACTION_TYPE = "TRANSFER";

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    private BehavioralDeviationRule rule;

    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        rule = new BehavioralDeviationRule(new ApplicationProperties(), evaluatedTransactionRepository);

        testEvent = new TransactionEvent();
        testEvent.setTransactionId(TRANSACTION_ID);
        testEvent.setAccountNumber(ACCOUNT_NUMBER);
        testEvent.setTransactionType(TRANSACTION_TYPE);
    }

    // ========== evaluateRule() Tests ==========
    // the baseline below is a mean of 100 with a standard deviation of 10

    @Test
    void shouldPass_whenTheAmountIsWithinThreeStandardDeviations() {
        givenTypeBaseline();
        testEvent.setAmount(new BigDecimal("120"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void shouldAddRiskWithoutFlagging_whenTheAmountIsBeyondThreeStandardDeviations() {
        givenTypeBaseline();
        testEvent.setAmount(new BigDecimal("140"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(80);
    }

    @Test
    void shouldFlag_whenTheAmountReachesFiveStandardDeviations() {
        givenTypeBaseline();
        testEvent.setAmount(new BigDecimal("150"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(100);
    }

    @Test
    void shouldPass_whenTheAccountsAmountsHaveNoSpread() {
        when(evaluatedTransactionRepository.findAmountStatsByTransactionType(TRANSACTION_TYPE, TRANSACTION_ID))
                .thenReturn(amountStats("100", "0"));
        testEvent.setAmount(new BigDecimal("10000"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void shouldPass_whenThereIsNotEnoughHistoryForAStandardDeviation() {
        when(evaluatedTransactionRepository.findAmountStatsByTransactionType(TRANSACTION_TYPE, TRANSACTION_ID))
                .thenReturn(amountStats("100", null));
        testEvent.setAmount(new BigDecimal("10000"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
    }

    // ========== Baseline Selection Tests ==========

    @Test
    void shouldCompareAgainstTheAccount_whenItHasEnoughHistory() {
        when(evaluatedTransactionRepository.countByAccountNumber(ACCOUNT_NUMBER)).thenReturn(25L);
        when(evaluatedTransactionRepository.findAmountStatsByAccountNumber(ACCOUNT_NUMBER, TRANSACTION_ID))
                .thenReturn(amountStats("100", "10"));
        testEvent.setAmount(new BigDecimal("150"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        verify(evaluatedTransactionRepository, never()).findAmountStatsByTransactionType(any(), any());
    }

    @Test
    void shouldCompareAgainstTheTransactionType_whenTheAccountIsNew() {
        when(evaluatedTransactionRepository.countByAccountNumber(ACCOUNT_NUMBER)).thenReturn(3L);
        when(evaluatedTransactionRepository.findAmountStatsByTransactionType(TRANSACTION_TYPE, TRANSACTION_ID))
                .thenReturn(amountStats("100", "10"));
        testEvent.setAmount(new BigDecimal("150"));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        verify(evaluatedTransactionRepository, never()).findAmountStatsByAccountNumber(any(), any());
    }

    // the transaction being checked is left out, otherwise it drags the baseline towards itself
    @Test
    void shouldExcludeTheTransactionBeingChecked_whenLoadingTheBaseline() {
        givenTypeBaseline();
        testEvent.setAmount(new BigDecimal("150"));

        rule.evaluateRule(testEvent);

        verify(evaluatedTransactionRepository).findAmountStatsByTransactionType(TRANSACTION_TYPE, TRANSACTION_ID);
    }

    // ========== Helper Methods ==========

    private void givenTypeBaseline() {
        when(evaluatedTransactionRepository.findAmountStatsByTransactionType(TRANSACTION_TYPE, TRANSACTION_ID))
                .thenReturn(amountStats("100", "10"));
    }

    private static EvaluatedTransactionRepository.AmountStats amountStats(final String average, final String standardDeviation) {
        return new EvaluatedTransactionRepository.AmountStats() {
            @Override
            public BigDecimal getAvgAmount() {
                return new BigDecimal(average);
            }

            @Override
            public BigDecimal getStdDevAmount() {
                return standardDeviation == null ? null : new BigDecimal(standardDeviation);
            }
        };
    }
}
