package com.fraudengine.core.event.handler;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.FraudCheckFailedEvent;
import com.fraudengine.core.event.domain.RuleEvaluation;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.outbox.OutboxWriter;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.RuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingEvaluationSweeper {

    public static final String RULES_DID_NOT_REPORT = "RULES_DID_NOT_REPORT";
    public static final String TRANSACTION_TYPE_NOT_SUPPORTED = "TRANSACTION_TYPE_NOT_SUPPORTED";

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final RuleHitRepository ruleHitRepository;
    private final ApplicationProperties applicationProperties;
    private final CompletionHandler completionHandler;
    private final OutboxWriter outboxWriter;

    @Scheduled(fixedDelayString = "${app.pending-evaluation.sweep-interval-ms:60000}")
    @Transactional
    public void sweep() {
        ApplicationProperties.PendingEvaluationConfig config = applicationProperties.getPendingEvaluation();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(config.getAbandonAfterMinutes()));

        evaluatedTransactionRepository.findAwaitingVerdictBefore(cutoff, config.getBatchSize())
                .forEach(this::resolve);
    }

    private void resolve(final EvaluatedTransaction transaction) {
        ApplicationProperties.TransactionTypeConfig typeConfig =
                applicationProperties.getTransactionTypes().get(transaction.getTransactionType());
        if (typeConfig == null) {
            abandon(transaction, TRANSACTION_TYPE_NOT_SUPPORTED, List.of());
            return;
        }

        // a completion can simply have been missed, so try again before giving up
        completionHandler.checkCompletion(asEvent(transaction));
        if (transaction.getFlagged() != null) {
            log.info("Completed a stuck transaction on retry: transactionId={}", transaction.getId());
            return;
        }

        abandon(transaction, RULES_DID_NOT_REPORT, missingRules(transaction, typeConfig));
    }

    private void abandon(final EvaluatedTransaction transaction, final String reason, final List<String> missingRules) {
        Instant failedAt = Instant.now();
        evaluatedTransactionRepository.markAbandoned(transaction.getId(), failedAt);
        outboxWriter.publish(FraudCheckFailedEvent.builder()
                .transactionId(transaction.getId())
                .accountNumber(transaction.getAccountNumber())
                .transactionType(transaction.getTransactionType())
                .reason(reason)
                .missingRules(missingRules)
                .ruleEvaluations(reportedRules(transaction))
                .firstSeenAt(transaction.getCreatedAt())
                .failedAt(failedAt)
                .build());

        log.warn("Gave up on a transaction without a verdict: transactionId={} reason={} missingRules={}",
                transaction.getId(), reason, missingRules);
    }

    private List<String> missingRules(final EvaluatedTransaction transaction,
                                      final ApplicationProperties.TransactionTypeConfig typeConfig) {
        Set<RuleType> reported = ruleHitRepository.findByTransactionId(transaction.getId()).stream()
                .map(RuleHit::getRuleType)
                .collect(Collectors.toSet());

        return typeConfig.getEnabledRules().stream()
                .filter(rule -> !reported.contains(rule))
                .map(RuleType::name)
                .toList();
    }

    private List<RuleEvaluation> reportedRules(final EvaluatedTransaction transaction) {
        return ruleHitRepository.findByTransactionId(transaction.getId()).stream()
                .map(hit -> RuleEvaluation.builder()
                        .ruleType(hit.getRuleType().name())
                        .status(hit.getStatus())
                        .flagged(hit.isFlagged())
                        .riskLevel(hit.getRiskLevel())
                        .build())
                .toList();
    }

    // completion only needs these three, and the row is all the sweeper has to work from
    private static TransactionEvent asEvent(final EvaluatedTransaction transaction) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(transaction.getId());
        event.setAccountNumber(transaction.getAccountNumber());
        event.setTransactionType(transaction.getTransactionType());
        return event;
    }
}
