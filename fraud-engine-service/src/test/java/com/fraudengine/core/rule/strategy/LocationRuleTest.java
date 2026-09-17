package com.fraudengine.core.rule.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationRuleTest {

    private static final String AREA_CODE = "JHB-001";

    @Mock
    private BadLocationRepository badLocationRepository;

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    private LocationRule rule;

    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        rule = new LocationRule(new ApplicationProperties(), badLocationRepository, evaluatedTransactionRepository);

        testEvent = new TransactionEvent();
        testEvent.setTransactionId("txn-1");
        testEvent.setTransactionType("TRANSFER");
        testEvent.setAreaCode(AREA_CODE);
    }

    // ========== evaluateRule() Tests ==========

    @Test
    void shouldSkipEvaluation_whenAreaCodeIsMissing() {
        testEvent.setAreaCode(null);

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.status()).isEqualTo(RuleHitStatus.SKIPPED_MISSING_DATA);
        assertThat(result.flagged()).isFalse();
        verifyNoInteractions(evaluatedTransactionRepository, badLocationRepository);
    }

    @Test
    void shouldStayAtLevelZero_whenAreaHasFewerThanMinimumTransactions() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(19, 19));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
        verify(badLocationRepository).upsertLevel(eq(AREA_CODE), eq(0), any(Instant.class));
    }

    @Test
    void shouldStayAtLevelZero_whenFraudRateIsBelowLevelOneThreshold() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(100, 29));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.status()).isEqualTo(RuleHitStatus.EVALUATED);
        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
    }

    @Test
    void shouldTierToLevelOne_whenFraudRateCrossesLevelOneThreshold() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(100, 30));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(30);
        verify(badLocationRepository).upsertLevel(eq(AREA_CODE), eq(1), any(Instant.class));
    }

    @Test
    void shouldTierToLevelTwo_whenFraudRateCrossesLevelTwoThreshold() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(100, 50));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(55);
        verify(badLocationRepository).upsertLevel(eq(AREA_CODE), eq(2), any(Instant.class));
    }

    @Test
    void shouldTierToLevelThree_whenFraudRateCrossesLevelThreeThreshold() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(100, 80));

        RuleResult result = rule.evaluateRule(testEvent);

        assertThat(result.flagged()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(80);
        verify(badLocationRepository).upsertLevel(eq(AREA_CODE), eq(3), any(Instant.class));
    }

    @Test
    void shouldPersistTheComputedLevel_regardlessOfWhetherItFlagged() {
        when(evaluatedTransactionRepository.findAreaStats(AREA_CODE)).thenReturn(areaStats(100, 80));

        rule.evaluateRule(testEvent);

        ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(badLocationRepository).upsertLevel(eq(AREA_CODE), levelCaptor.capture(), any(Instant.class));

        assertThat(levelCaptor.getValue()).isEqualTo(3);
    }

    // ========== Helper Methods ==========

    private static EvaluatedTransactionRepository.AreaStats areaStats(final long total, final long flagged) {
        return new EvaluatedTransactionRepository.AreaStats() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getFlagged() {
                return flagged;
            }
        };
    }
}
