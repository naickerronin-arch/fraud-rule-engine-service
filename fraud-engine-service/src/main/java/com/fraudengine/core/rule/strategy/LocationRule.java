package com.fraudengine.core.rule.strategy;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.event.domain.TransactionEvent;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationRule implements FraudRule {

    private final ApplicationProperties applicationProperties;
    private final BadLocationRepository badLocationRepository;
    private final EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Override
    public RuleType ruleType() {
        return RuleType.GEO;
    }

    @Override
    public boolean canFlagStandalone() {
        return false;
    }

    @Override
    public boolean isEnabledFor(final String transactionType) {
        ApplicationProperties.TransactionTypeConfig config = applicationProperties.getTransactionTypes().get(transactionType);
        return config != null && config.getEnabledRules().contains(ruleType());
    }

    @Override
    public RuleResult evaluateRule(final TransactionEvent transaction) {
        String areaCode = transaction.getAreaCode();
        if (areaCode == null || areaCode.isEmpty()) {
            log.trace("missing data for transaction {}", transaction.getTransactionId());
            return RuleResult.builder()
                    .status(RuleHitStatus.SKIPPED_MISSING_DATA)
                    .flagged(false)
                    .riskLevel(0)
                    .build();
        }

        int level = computeLevel(areaCode);
        log.trace("Level for area {} is :{}",transaction.getAreaCode(), level);
        badLocationRepository.upsertLevel(areaCode, level, Instant.now());

        return RuleResult.builder()
                .status(RuleHitStatus.EVALUATED)
                .flagged(level > 0) // only flag
                .riskLevel(riskLevelFor(level))
                .build();
    }

    private int computeLevel(final String areaCode) {
        ApplicationProperties.LocationConfig config = applicationProperties.getLocationConfig();

        EvaluatedTransactionRepository.AreaStats stats = evaluatedTransactionRepository.findAreaStats(areaCode);
        long total = stats.getTotal();
        if (total < config.getMinTransactionCount()) {
            return 0;
        }

        long flagged = stats.getFlagged();
        double percentage = (flagged * 100.0) / total;

        if (percentage >= config.getLevelThreeThresholdPercent()) {
            return 3;
        }
        if (percentage >= config.getLevelTwoThresholdPercent()) {
            return 2;
        }
        if (percentage >= config.getLevelOneThresholdPercent()) {
            return 1;
        }
        return 0;
    }

    private int riskLevelFor(final int level) {
        ApplicationProperties.LocationConfig config = applicationProperties.getLocationConfig();
        switch (level) {
            case 3:
                return config.getLevelThreeRiskLevel();
            case 2:
                return config.getLevelTwoRiskLevel();
            case 1:
                return config.getLevelOneRiskLevel();
            default:
                return 0;
        }
    }
}
