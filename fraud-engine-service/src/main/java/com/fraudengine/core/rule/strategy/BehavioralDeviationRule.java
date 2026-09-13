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

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class BehavioralDeviationRule implements FraudRule {

    private final ApplicationProperties properties;
    private final EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Override
    public RuleType ruleType() {
        return RuleType.BEHAVIORAL_DEVIATION;
    }

    @Override
    public boolean isEnabledFor(final String transactionType) {
        ApplicationProperties.TransactionTypeConfig config = properties.getTransactionTypes().get(transactionType);
        return config != null && config.getEnabledRules().contains(ruleType());
    }

    @Override
    public RuleResult evaluateRule(final TransactionEvent transaction) {
        ApplicationProperties.BehavioralDeviationConfig config = properties.getBehavioralDeviationConfig();
        String accountNumber = transaction.getAccountNumber();

        long lifetimeCount = evaluatedTransactionRepository.countByAccountNumber(accountNumber);
        boolean baseLine = lifetimeCount >= config.getMinHistoryCount();

        EvaluatedTransactionRepository.AmountStats stats = baseLine
                ? evaluatedTransactionRepository.findAmountStatsByAccountNumber(accountNumber, transaction.getTransactionId())
                : evaluatedTransactionRepository.findAmountStatsByTransactionType(transaction.getTransactionType(), transaction.getTransactionId());

        if (stats == null || stats.getStdDevAmount() == null || stats.getStdDevAmount().signum() == 0) {
            return RuleResult.builder()
                    .status(RuleHitStatus.EVALUATED)
                    .flagged(false)
                    .riskLevel(0)
                    .build();
        }

        BigDecimal deviation = transaction.getAmount().subtract(stats.getAvgAmount()).abs();
        double stdDevsFromMean = deviation.doubleValue() / stats.getStdDevAmount().doubleValue();

        boolean meetsConfidenceThreshold = stdDevsFromMean >= config.getAlertThreshold();
        boolean hasDeviation = stdDevsFromMean > config.getStdDevThreshold();

        if (!hasDeviation) {
            return RuleResult.builder()
                    .status(RuleHitStatus.EVALUATED)
                    .flagged(false)
                    .riskLevel(0)
                    .build();
        }

        double riskPercentage = (stdDevsFromMean / config.getAlertThreshold()) * 100;
        int riskLevel = (int) Math.min(100, riskPercentage);

        return RuleResult.builder()
                .status(RuleHitStatus.EVALUATED)
                .flagged(meetsConfidenceThreshold)
                .riskLevel(riskLevel)
                .build();
    }
}
