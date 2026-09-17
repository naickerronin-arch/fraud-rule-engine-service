package com.fraudengine.core.event.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.exception.UnsupportedTransactionTypeException;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleHandlerTest {

    private static final String ACCOUNT_NUMBER = "ACC-1";
    private static final String AREA_CODE = "JHB-001";
    private static final String TRANSACTION_ID = "txn-1";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private RuleHitRepository ruleHitRepository;

    @Mock
    private CompletionHandler completionHandler;

    @Mock
    private FraudRule rule;

    private RuleHandler handler;

    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.TransactionTypeConfig transfer = new ApplicationProperties.TransactionTypeConfig();
        transfer.setEnabledRules(List.of(RuleType.VELOCITY, RuleType.GEO));
        properties.getTransactionTypes().put("TRANSFER", transfer);
        properties.getTransactionTypes().put("NO_RULES", new ApplicationProperties.TransactionTypeConfig());

        handler = new RuleHandler(evaluatedTransactionRepository, ruleHitRepository, properties, completionHandler);

        testEvent = new TransactionEvent();
        testEvent.setTransactionId(TRANSACTION_ID);
        testEvent.setAccountNumber(ACCOUNT_NUMBER);
        testEvent.setAmount(new BigDecimal("250.00"));
        testEvent.setTransactionType("TRANSFER");
        testEvent.setAreaCode(AREA_CODE);
        testEvent.setTimestamp(NOW);
    }

    // ========== validate() Tests ==========

    // an unsupported type isn't retryable, so it goes straight to the DLT rather than looping
    @Test
    void shouldReject_whenTheTransactionTypeIsNotConfigured() {
        testEvent.setTransactionType("PAYMENT");

        assertThatThrownBy(() -> handler.handle(testEvent, rule))
                .isInstanceOf(UnsupportedTransactionTypeException.class);

        verifyNoInteractions(evaluatedTransactionRepository, ruleHitRepository, completionHandler);
    }

    @Test
    void shouldReject_whenTheTransactionTypeHasNoRules() {
        testEvent.setTransactionType("NO_RULES");

        assertThatThrownBy(() -> handler.handle(testEvent, rule))
                .isInstanceOf(UnsupportedTransactionTypeException.class);

        verifyNoInteractions(evaluatedTransactionRepository, ruleHitRepository, completionHandler);
    }

    // ========== handle() Tests ==========

    // the row is still recorded, because the history-based rules count every transaction
    @Test
    void shouldRecordTheTransactionOnly_whenTheRuleIsNotEnabledForTheType() {
        when(rule.isEnabledFor("TRANSFER")).thenReturn(false);

        handler.handle(testEvent, rule);

        verify(evaluatedTransactionRepository)
                .upsert(TRANSACTION_ID, ACCOUNT_NUMBER, new BigDecimal("250.00"), "TRANSFER", AREA_CODE, NOW);
        verify(rule, never()).evaluateRule(any());
        verifyNoInteractions(ruleHitRepository, completionHandler);
    }

    @Test
    void shouldRecordTheRuleHitAndCheckCompletion_whenTheRuleIsEnabled() {
        when(rule.isEnabledFor("TRANSFER")).thenReturn(true);
        when(rule.ruleType()).thenReturn(RuleType.VELOCITY);
        when(rule.evaluateRule(testEvent)).thenReturn(RuleResult.builder()
                .status(RuleHitStatus.EVALUATED)
                .flagged(true)
                .riskLevel(100)
                .build());

        handler.handle(testEvent, rule);

        verify(evaluatedTransactionRepository)
                .upsert(TRANSACTION_ID, ACCOUNT_NUMBER, new BigDecimal("250.00"), "TRANSFER", AREA_CODE, NOW);
        verify(ruleHitRepository).upsert(eq(TRANSACTION_ID), eq("VELOCITY"), eq("EVALUATED"), eq(true), eq(100), any(Instant.class));
        verify(completionHandler).checkCompletion(testEvent);
    }
}
