package com.fraudengine.core.rule;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.persistence.entity.RuleHit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@AllArgsConstructor
@Component
public class RiskScoreCalculator {

    private final ApplicationProperties applicationProperties;

    public int calculateWeightedScore(final List<RuleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return 0;
        }

        double weighted = 0.0;
        double totalWeight = 0.0;
        for (RuleHit hit : hits) {
            if (hit.getStatus() == RuleHitStatus.SKIPPED_MISSING_DATA) {
                continue;
            }

            double weight = getWeight(hit.getRuleType());
            weighted += hit.getRiskLevel() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return 0;
        }
        double normalized = weighted / totalWeight;
        return (int) Math.min(100, normalized);
    }

    private double getWeight(final RuleType ruleType) {
        switch (ruleType) {
            case VELOCITY:
                return applicationProperties.getVelocityConfig().getWeight();
            case GEO:
                return applicationProperties.getLocationConfig().getWeight();
            case BEHAVIORAL_DEVIATION:
                return applicationProperties.getBehavioralDeviationConfig().getWeight();
            default:
                return 0.33;
        }
    }
}
