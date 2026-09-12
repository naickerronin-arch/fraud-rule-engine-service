package com.fraudengine.core.event.handler;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.exception.FraudEngineErrorMessages;
import com.fraudengine.core.exception.TransactionValidationException;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.event.domain.RuleEvaluation;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.metrics.MetricsRecorder;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RiskScoreCalculator;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleHandler {

    private static final String METRIC_FRAUD_CHECK_COMPLETE = "fraud.check.complete";
    private static final String METRIC_RULE_EVALUATED = "fraud.rule.evaluated";

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final RuleHitRepository ruleHitRepository;
    private final ApplicationProperties applicationProperties;
    private final Validator validator;
    private final MetricsRecorder metricsRecorder;
    private final RiskScoreCalculator riskScoreCalculator;
    private final OutboxWriter outboxWriter;
    private final List<FraudRule> rules;

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
                event.getTimestamp());

        if (!rule.isEnabledFor(event.getTransactionType())) {
            return; // rule is not enabled
        }

        RuleResult result = rule.evaluateRule(event);

        ruleHitRepository.upsert(
                event.getTransactionId(),
                rule.ruleType().name(),
                result.status().name(),
                result.flagged(),
                result.riskLevel(),
                Instant.now());

        metricsRecorder.increment(METRIC_RULE_EVALUATED,
                "rule", rule.ruleType().name(),
                "flagged", String.valueOf(result.flagged()));

        Optional<FraudCheckCompleteEvent> completeCheck = evaluateCompletion(event);

        completeCheck.ifPresent(completeEvent ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        recordFraudCheckComplete(completeEvent);
                    }
                }));
    }

    private void validate(final TransactionEvent event) {
        Set<ConstraintViolation<TransactionEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new TransactionValidationException(FraudEngineErrorMessages.TRANSACTION_VALIDATION_FAILED);
        }
    }

    private Optional<FraudCheckCompleteEvent> evaluateCompletion(final TransactionEvent event) {
        ApplicationProperties.TransactionTypeConfig config =
                applicationProperties.getTransactionTypes().get(event.getTransactionType());
        if (config == null) {
            return Optional.empty();
        }

        List<RuleHit> hits = ruleHitRepository.findByTransactionId(event.getTransactionId());
        int expected = config.getEnabledRules().size();

        if (hits.size() < expected) {
            return Optional.empty();
        }

        List<RuleEvaluation> ruleEvaluations = hits.stream()
                .map(hit -> RuleEvaluation.builder()
                        .ruleType(hit.getRuleType().name())
                        .status(hit.getStatus())
                        .flagged(hit.isFlagged())
                        .riskLevel(hit.getRiskLevel())
                        .build())
                .collect(Collectors.toList());

        int weightedRiskScore = riskScoreCalculator.calculateWeightedScore(hits);
        boolean flagged = isFlagged(hits, weightedRiskScore);

        int claimed = evaluatedTransactionRepository.claimCompletion(event.getTransactionId(), flagged);
        if (claimed == 0) {
            return Optional.empty();
        }

        return Optional.of(FraudCheckCompleteEvent.builder()
                .transactionId(event.getTransactionId())
                .completedAt(Instant.now())
                .accountNumber(event.getAccountNumber())
                .highestRiskLevel(hits.stream().mapToInt(RuleHit::getRiskLevel).max().orElse(0))
                .flagged(flagged)
                .transactionType(event.getTransactionType())
                .ruleEvaluations(ruleEvaluations)
                .weightedRiskScore(weightedRiskScore)
                .build());
    }

    private boolean isFlagged(final List<RuleHit> hits, final int weightedRiskScore) {
        Map<RuleType, Boolean> standaloneByType = rules.stream()
                .collect(Collectors.toMap(FraudRule::ruleType, FraudRule::canFlagStandalone));

        boolean flaggedByStandaloneRule = hits.stream()
                .anyMatch(hit -> hit.isFlagged() && standaloneByType.getOrDefault(hit.getRuleType(), true));

        return flaggedByStandaloneRule || weightedRiskScore >= applicationProperties.getOverallFlagThreshold();
    }

    private void recordFraudCheckComplete(final FraudCheckCompleteEvent completeEvent) {
        metricsRecorder.increment(METRIC_FRAUD_CHECK_COMPLETE, "flagged", String.valueOf(completeEvent.isFlagged()));
        outboxWriter.publish(completeEvent);
    }
}
