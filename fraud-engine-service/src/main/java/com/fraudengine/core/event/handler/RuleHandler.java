package com.fraudengine.core.event.handler;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.exception.FraudEngineErrorMessages;
import com.fraudengine.core.exception.UnsupportedTransactionTypeException;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleHandler {

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final RuleHitRepository ruleHitRepository;
    private final ApplicationProperties applicationProperties;
    private final CompletionHandler completionHandler;

    @Transactional
    public void handle(final TransactionEvent event, final FraudRule rule) {
        validate(event);

        log.trace("Upserting transaction event {} for rule evaluation", event.getTransactionId());

        evaluatedTransactionRepository.upsert(
                event.getTransactionId(),
                event.getAccountNumber(),
                event.getAmount(),
                event.getTransactionType(),
                event.getAreaCode(),
                event.getTimestamp());// idempotent upsert, sets the evaluated row for which ever consumer gets here first

        if (!rule.isEnabledFor(event.getTransactionType())) {
            return; // rule is not enabled
        }

        RuleResult result = rule.evaluateRule(event);// strategy to evaluate the rule of the specific event

        ruleHitRepository.upsert(
                event.getTransactionId(),
                rule.ruleType().name(),
                result.status().name(),
                result.flagged(),
                result.riskLevel(),
                Instant.now());
        log.info("Rule hit recorded: transactionId={} rule={} status={} flagged={} riskLevel={}",
                event.getTransactionId(), rule.ruleType(), result.status(), result.flagged(), result.riskLevel());

        completionHandler.checkCompletion(event);
    }

    private void validate(final TransactionEvent event) {
        ApplicationProperties.TransactionTypeConfig config = applicationProperties.getTransactionTypes().get(event.getTransactionType());
        if (config == null || config.getEnabledRules().isEmpty()) {
            throw new UnsupportedTransactionTypeException(FraudEngineErrorMessages.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
    }
}
