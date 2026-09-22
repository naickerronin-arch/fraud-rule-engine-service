package com.fraudengine.core.event.handler;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.event.domain.RuleEvaluation;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RiskScoreCalculator;
import com.fraudengine.core.rule.RuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompletionHandler {

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final RuleHitRepository ruleHitRepository;
    private final ApplicationProperties applicationProperties;
    private final RiskScoreCalculator riskScoreCalculator;
    private final OutboxWriter outboxWriter;
    private final List<FraudRule> rules;

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkCompletion(final TransactionEvent event) {
        ApplicationProperties.TransactionTypeConfig config =
                applicationProperties.getTransactionTypes().get(event.getTransactionType());

        EvaluatedTransaction transaction = evaluatedTransactionRepository
                .findLockedById(event.getTransactionId())
                .orElseThrow(); // db locking operation to ensure rules dont race and fail to complete/ publish end event
        if (transaction.getFlagged() != null) {
            return; // already completed
        }

        List<RuleHit> hits = ruleHitRepository.findByTransactionId(event.getTransactionId());
        if (hits.size() < config.getEnabledRules().size()) {
            return; // not completed yet
        }

        int weightedRiskScore = riskScoreCalculator.calculateWeightedScore(hits);
        boolean flagged = isFlagged(hits, weightedRiskScore);
        transaction.setFlagged(flagged);
        evaluatedTransactionRepository.save(transaction);

        FraudCheckCompleteEvent completeEvent = buildCompleteEvent(event, hits, flagged, weightedRiskScore);
        outboxWriter.publish(completeEvent);
        log.info("Fraud check complete: transactionId={} flagged={} weightedRiskScore={}",
                event.getTransactionId(), flagged, weightedRiskScore);
    }

    private boolean isFlagged(final List<RuleHit> hits, final int weightedRiskScore) {
        Set<RuleType> standaloneRules = rules.stream()
                .filter(FraudRule::canFlagStandalone)
                .map(FraudRule::ruleType)
                .collect(Collectors.toSet());

        boolean flaggedByStandaloneRule = hits.stream()
                .anyMatch(hit -> hit.isFlagged() && standaloneRules.contains(hit.getRuleType()));

        return flaggedByStandaloneRule || weightedRiskScore >= applicationProperties.getOverallFlagThreshold();
    }

    private FraudCheckCompleteEvent buildCompleteEvent(final TransactionEvent event, final List<RuleHit> hits,
                                                       final boolean flagged, final int weightedRiskScore) {
        List<RuleEvaluation> ruleEvaluations = hits.stream()
                .map(hit -> RuleEvaluation.builder()
                        .ruleType(hit.getRuleType().name())
                        .status(hit.getStatus())
                        .flagged(hit.isFlagged())
                        .riskLevel(hit.getRiskLevel())
                        .build())
                .collect(Collectors.toList());

        return FraudCheckCompleteEvent.builder()
                .transactionId(event.getTransactionId())
                .completedAt(Instant.now())
                .accountNumber(event.getAccountNumber())
                .highestRiskLevel(hits.stream().mapToInt(RuleHit::getRiskLevel).max().orElse(0))
                .flagged(flagged)
                .transactionType(event.getTransactionType())
                .ruleEvaluations(ruleEvaluations)
                .weightedRiskScore(weightedRiskScore)
                .build();
    }
}
