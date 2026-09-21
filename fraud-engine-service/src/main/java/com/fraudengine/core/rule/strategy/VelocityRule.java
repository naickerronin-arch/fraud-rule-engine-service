package com.fraudengine.core.rule.strategy;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

    private final ApplicationProperties applicationProperties;
    private final EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Override
    public RuleType ruleType() {
        return RuleType.VELOCITY;
    }

    @Override
    public boolean isEnabledFor(final String transactionType) {
        ApplicationProperties.TransactionTypeConfig config = applicationProperties.getTransactionTypes().get(transactionType);
        return config != null && config.getEnabledRules().contains(ruleType());
    }

    @Override
    public RuleResult evaluateRule(final TransactionEvent transactionEvent) {
        String accountNumber = transactionEvent.getAccountNumber();

        // establish window start period based on config
        Instant windowStart = transactionEvent.getTimestamp().minus(Duration.ofMinutes(applicationProperties.getVelocityConfig().getWindowMinutes()));

        // window counts within period
        long windowCount = evaluatedTransactionRepository.countByAccountNumberAndEventTimeAfter(accountNumber, windowStart);

        double threshold = fetchBaseLine(applicationProperties.getVelocityConfig(), transactionEvent);
        double alertThreshold = threshold * applicationProperties.getVelocityConfig().getAlertThreshold();

        boolean meetsConfidenceThreshold = windowCount >= alertThreshold;
        boolean hasVelocity = windowCount >= threshold;

        if (!hasVelocity) {
            log.trace("Velocity Rule passed for Transaction {}", transactionEvent.getTransactionId());
            return RuleResult.builder()
                    .status(RuleHitStatus.EVALUATED)
                    .flagged(false)
                    .riskLevel(0)
                    .build();
        }
        double riskPercentage = (windowCount / alertThreshold) * 100;
        int riskLevel = (int) Math.min(100, riskPercentage);

        return RuleResult.builder()
                .status(RuleHitStatus.EVALUATED)
                .flagged(meetsConfidenceThreshold)
                .riskLevel(riskLevel)
                .build();
    }


    private double fetchBaseLine(final ApplicationProperties.VelocityConfig velocityConfig, final TransactionEvent transaction) {

        String accountNumber = transaction.getAccountNumber();

        long allTransactions = evaluatedTransactionRepository.countByAccountNumber(accountNumber);

        if (allTransactions < velocityConfig.getMinHistoryCount()) {
            log.trace("using default max transaction for baseLine for transaction {}", transaction.getTransactionId());
            return velocityConfig.getDefaultMaxTransactions(); // default to config baseLine since not enough data is available
        }

        Instant transactionWindowStart = transaction.getTimestamp().minus(Duration.ofDays(velocityConfig.getTransactionWindow()));
        long totalTransactionsInPeriod = evaluatedTransactionRepository.countByAccountNumberAndEventTimeAfter(accountNumber, transactionWindowStart);

        long windowsInPeriod= Duration.ofDays(velocityConfig.getTransactionWindow()).toMinutes()
                / velocityConfig.getWindowMinutes();
        double averageTransactions = (double) totalTransactionsInPeriod / windowsInPeriod;

        return Math.max(velocityConfig.getDefaultMaxTransactions(), averageTransactions * velocityConfig.getMultiplier());
    }
}
