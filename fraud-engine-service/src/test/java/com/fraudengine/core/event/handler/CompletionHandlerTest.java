package com.fraudengine.core.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RiskScoreCalculator;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompletionHandlerTest {

    private static final String TRANSACTION_ID = "txn-1";

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private RuleHitRepository ruleHitRepository;

    @Mock
    private RiskScoreCalculator riskScoreCalculator;

    @Mock
    private OutboxWriter outboxWriter;

    private CompletionHandler handler;

    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.TransactionTypeConfig transfer = new ApplicationProperties.TransactionTypeConfig();
        transfer.setEnabledRules(List.of(RuleType.VELOCITY, RuleType.GEO, RuleType.BEHAVIORAL_DEVIATION));
        properties.getTransactionTypes().put("TRANSFER", transfer);

        List<FraudRule> rules = List.of(
                stubRule(RuleType.VELOCITY, true),
                stubRule(RuleType.GEO, false),
                stubRule(RuleType.BEHAVIORAL_DEVIATION, true));

        handler = new CompletionHandler(
                evaluatedTransactionRepository, ruleHitRepository, properties, riskScoreCalculator, outboxWriter, rules);

        testEvent = new TransactionEvent();
        testEvent.setTransactionId(TRANSACTION_ID);
        testEvent.setAccountNumber("ACC-1");
        testEvent.setTransactionType("TRANSFER");
    }

    // ========== checkCompletion() Tests ==========

    // the lock plus this guard is what stops a redelivery publishing a second verdict
    @Test
    void shouldDoNothing_whenTheTransactionAlreadyHasAVerdict() {
        EvaluatedTransaction transaction = new EvaluatedTransaction();
        transaction.setFlagged(false);
        when(evaluatedTransactionRepository.findLockedById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        handler.checkCompletion(testEvent);

        verify(evaluatedTransactionRepository, never()).save(any());
        verifyNoInteractions(ruleHitRepository, outboxWriter);
    }

    @Test
    void shouldWait_whenNotEveryEnabledRuleHasReported() {
        EvaluatedTransaction transaction = givenPendingTransaction();
        when(ruleHitRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(List.of(
                hit(RuleType.VELOCITY, false, 0),
                hit(RuleType.GEO, false, 0)));

        handler.checkCompletion(testEvent);

        assertThat(transaction.getFlagged()).isNull();
        verify(evaluatedTransactionRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    // ========== Verdict Tests ==========

    @Test
    void shouldFlag_whenAStandaloneRuleFlaggedBelowTheThreshold() {
        EvaluatedTransaction transaction = givenAllRulesReported(velocityFlagged(), 40);

        handler.checkCompletion(testEvent);

        assertThat(transaction.getFlagged()).isTrue();
        verify(evaluatedTransactionRepository).save(transaction);
        assertThat(publishedEvent().isFlagged()).isTrue();
    }

    // location detects a bad area, not a bad transaction, so it can never flag on its own
    @Test
    void shouldNotFlag_whenOnlyACorroborationOnlyRuleFlagged() {
        EvaluatedTransaction transaction = givenAllRulesReported(geoFlagged(), 26);

        handler.checkCompletion(testEvent);

        assertThat(transaction.getFlagged()).isFalse();
        assertThat(publishedEvent().isFlagged()).isFalse();
    }

    @Test
    void shouldNotFlag_whenTheWeightedScoreIsJustUnderTheThreshold() {
        EvaluatedTransaction transaction = givenAllRulesReported(geoFlagged(), 49);

        handler.checkCompletion(testEvent);

        assertThat(transaction.getFlagged()).isFalse();
    }

    @Test
    void shouldFlag_whenTheWeightedScoreReachesTheThreshold() {
        EvaluatedTransaction transaction = givenAllRulesReported(geoFlagged(), 50);

        handler.checkCompletion(testEvent);

        assertThat(transaction.getFlagged()).isTrue();
        assertThat(publishedEvent().isFlagged()).isTrue();
    }

    // ========== Published Event Tests ==========

    @Test
    void shouldPublishEveryRulesResult_whenTheVerdictIsReached() {
        givenAllRulesReported(velocityFlagged(), 40);

        handler.checkCompletion(testEvent);

        FraudCheckCompleteEvent published = publishedEvent();
        assertThat(published.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(published.getAccountNumber()).isEqualTo("ACC-1");
        assertThat(published.isFlagged()).isTrue();
        assertThat(published.getWeightedRiskScore()).isEqualTo(40);
        assertThat(published.getHighestRiskLevel()).isEqualTo(100);
        assertThat(published.getRuleEvaluations()).hasSize(3);
    }

    // ========== Helper Methods ==========

    private EvaluatedTransaction givenPendingTransaction() {
        EvaluatedTransaction transaction = new EvaluatedTransaction();
        transaction.setId(TRANSACTION_ID);
        when(evaluatedTransactionRepository.findLockedById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private EvaluatedTransaction givenAllRulesReported(final List<RuleHit> hits, final int weightedRiskScore) {
        EvaluatedTransaction transaction = givenPendingTransaction();
        when(ruleHitRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(hits);
        when(riskScoreCalculator.calculateWeightedScore(hits)).thenReturn(weightedRiskScore);
        return transaction;
    }

    private FraudCheckCompleteEvent publishedEvent() {
        ArgumentCaptor<FraudCheckCompleteEvent> captor = ArgumentCaptor.forClass(FraudCheckCompleteEvent.class);
        verify(outboxWriter).publish(captor.capture());
        return captor.getValue();
    }

    private static List<RuleHit> velocityFlagged() {
        return List.of(
                hit(RuleType.VELOCITY, true, 100),
                hit(RuleType.GEO, false, 0),
                hit(RuleType.BEHAVIORAL_DEVIATION, false, 0));
    }

    private static List<RuleHit> geoFlagged() {
        return List.of(
                hit(RuleType.VELOCITY, false, 0),
                hit(RuleType.GEO, true, 80),
                hit(RuleType.BEHAVIORAL_DEVIATION, false, 0));
    }

    private static RuleHit hit(final RuleType ruleType, final boolean flagged, final int riskLevel) {
        RuleHit hit = new RuleHit();
        hit.setTransactionId(TRANSACTION_ID);
        hit.setRuleType(ruleType);
        hit.setStatus(RuleHitStatus.EVALUATED);
        hit.setFlagged(flagged);
        hit.setRiskLevel(riskLevel);
        return hit;
    }

    private static FraudRule stubRule(final RuleType ruleType, final boolean canFlagStandalone) {
        return new FraudRule() {
            @Override
            public RuleType ruleType() {
                return ruleType;
            }

            @Override
            public boolean isEnabledFor(final String transactionType) {
                return true;
            }

            @Override
            public RuleResult evaluateRule(final TransactionEvent transaction) {
                throw new UnsupportedOperationException("the completion handler never evaluates rules");
            }

            @Override
            public boolean canFlagStandalone() {
                return canFlagStandalone;
            }
        };
    }
}
