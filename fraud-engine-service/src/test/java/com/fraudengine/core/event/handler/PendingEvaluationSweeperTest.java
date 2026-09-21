package com.fraudengine.core.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.FraudCheckFailedEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingEvaluationSweeperTest {

    private static final String TRANSACTION_ID = "txn-1";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private RuleHitRepository ruleHitRepository;

    @Mock
    private CompletionHandler completionHandler;

    @Mock
    private OutboxWriter outboxWriter;

    private ApplicationProperties properties;

    private PendingEvaluationSweeper sweeper;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        ApplicationProperties.TransactionTypeConfig transfer = new ApplicationProperties.TransactionTypeConfig();
        transfer.setEnabledRules(List.of(RuleType.VELOCITY, RuleType.GEO, RuleType.BEHAVIORAL_DEVIATION));
        properties.getTransactionTypes().put("TRANSFER", transfer);

        sweeper = new PendingEvaluationSweeper(
                evaluatedTransactionRepository, ruleHitRepository, properties, completionHandler, outboxWriter);
    }

    // ========== sweep() Tests ==========

    // the clock is when this service first saw the transaction, not the producer's event time
    @Test
    void shouldOnlyLookAtTransactionsOlderThanTheThreshold_whenSweeping() {
        when(evaluatedTransactionRepository.findAwaitingVerdictBefore(any(), anyInt())).thenReturn(List.of());

        sweeper.sweep();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(evaluatedTransactionRepository).findAwaitingVerdictBefore(cutoff.capture(), eq(50));
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(Duration.ofMinutes(4)));
        verifyNoInteractions(outboxWriter);
    }

    // a completion can simply have been missed, so the cheap fix is tried first
    @Test
    void shouldCompleteTheTransaction_whenEveryRuleHasSinceReported() {
        EvaluatedTransaction transaction = givenStuckTransaction("TRANSFER");
        doAnswer(invocation -> {
            transaction.setFlagged(true);
            return null;
        }).when(completionHandler).checkCompletion(any());

        sweeper.sweep();

        verify(evaluatedTransactionRepository, never()).markAbandoned(anyString(), any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void shouldPublishTheRulesThatNeverReported_whenTheTransactionIsStillPending() {
        EvaluatedTransaction transaction = givenStuckTransaction("TRANSFER");
        when(ruleHitRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(List.of(hit(RuleType.VELOCITY)));

        sweeper.sweep();

        verify(evaluatedTransactionRepository).markAbandoned(eq(TRANSACTION_ID), any(Instant.class));

        FraudCheckFailedEvent published = publishedEvent();
        assertThat(published.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(published.getAccountNumber()).isEqualTo("ACC-1");
        assertThat(published.getReason()).isEqualTo(PendingEvaluationSweeper.RULES_DID_NOT_REPORT);
        assertThat(published.getMissingRules()).containsExactly("GEO", "BEHAVIORAL_DEVIATION");
        assertThat(published.getRuleEvaluations()).hasSize(1);
        assertThat(published.getFirstSeenAt()).isEqualTo(CREATED_AT);
        assertThat(published.getFailedAt()).isNotNull();
    }

    // the type can be dropped from the configuration while transactions are still in flight
    @Test
    void shouldGiveUpWithoutRetrying_whenTheTransactionTypeIsNoLongerConfigured() {
        givenStuckTransaction("CRYPTO");

        sweeper.sweep();

        verifyNoInteractions(completionHandler);
        verify(evaluatedTransactionRepository).markAbandoned(eq(TRANSACTION_ID), any(Instant.class));
        assertThat(publishedEvent().getReason()).isEqualTo(PendingEvaluationSweeper.TRANSACTION_TYPE_NOT_SUPPORTED);
    }

    // ========== Helper Methods ==========

    private EvaluatedTransaction givenStuckTransaction(final String transactionType) {
        EvaluatedTransaction transaction = new EvaluatedTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setAccountNumber("ACC-1");
        transaction.setTransactionType(transactionType);
        transaction.setCreatedAt(CREATED_AT);
        when(evaluatedTransactionRepository.findAwaitingVerdictBefore(any(), anyInt())).thenReturn(List.of(transaction));
        return transaction;
    }

    private FraudCheckFailedEvent publishedEvent() {
        ArgumentCaptor<FraudCheckFailedEvent> captor = ArgumentCaptor.forClass(FraudCheckFailedEvent.class);
        verify(outboxWriter).publish(captor.capture());
        return captor.getValue();
    }

    private static RuleHit hit(final RuleType ruleType) {
        RuleHit hit = new RuleHit();
        hit.setTransactionId(TRANSACTION_ID);
        hit.setRuleType(ruleType);
        hit.setStatus(RuleHitStatus.EVALUATED);
        hit.setFlagged(false);
        hit.setRiskLevel(50);
        return hit;
    }
}
