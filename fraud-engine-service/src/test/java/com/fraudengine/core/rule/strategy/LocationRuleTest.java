package com.fraudengine.core.rule.strategy;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocationRuleTest {

    private EvaluatedTransactionRepository repository;
    private BadLocationRepository badLocationRepository;
    private LocationRule rule;

    @BeforeEach
    void setUp() {
        repository = mock(EvaluatedTransactionRepository.class);
        badLocationRepository = mock(BadLocationRepository.class);
        rule = new LocationRule(new ApplicationProperties(), badLocationRepository, repository);
    }

    @Test
    void skipsTransactionsWithoutAnAreaCode() {
        RuleResult result = rule.evaluateRule(event(null));

        assertThat(result.status()).isEqualTo(RuleHitStatus.SKIPPED_MISSING_DATA);
        assertThat(result.flagged()).isFalse();
        verifyNoInteractions(repository, badLocationRepository);
    }

    @Test
    void staysAtLevelZeroUntilTheAreaHasEnoughTransactions() {
        when(repository.findAreaStats("JHB-001")).thenReturn(areaStats(19, 19));

        RuleResult result = rule.evaluateRule(event("JHB-001"));

        assertThat(result.flagged()).isFalse();
        assertThat(result.riskLevel()).isZero();
        verify(badLocationRepository).upsertLevel(eq("JHB-001"), eq(0), any(Instant.class));
    }

    @ParameterizedTest
    @CsvSource({
        "29, 0, 0",
        "30, 1, 30",
        "50, 2, 55",
        "80, 3, 80"
    })
    void tiersTheAreaByItsFraudRate(final long flaggedOutOfHundred, final int level, final int riskLevel) {
        when(repository.findAreaStats("JHB-001")).thenReturn(areaStats(100, flaggedOutOfHundred));

        RuleResult result = rule.evaluateRule(event("JHB-001"));

        assertThat(result.status()).isEqualTo(RuleHitStatus.EVALUATED);
        assertThat(result.flagged()).isEqualTo(level > 0);
        assertThat(result.riskLevel()).isEqualTo(riskLevel);
        verify(badLocationRepository).upsertLevel(eq("JHB-001"), eq(level), any(Instant.class));
    }

    private static TransactionEvent event(final String areaCode) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId("txn-1");
        event.setTransactionType("TRANSFER");
        event.setAreaCode(areaCode);
        return event;
    }

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
