package com.fraudengine.core.rule;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.persistence.entity.RuleHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoreCalculatorTest {

    private RiskScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getVelocityConfig().setWeight(0.5);
        properties.getLocationConfig().setWeight(0.25);
        properties.getBehavioralDeviationConfig().setWeight(0.25);
        calculator = new RiskScoreCalculator(properties);
    }

    @Test
    void returnsZeroWhenThereAreNoHits() {
        assertThat(calculator.calculateWeightedScore(null)).isZero();
        assertThat(calculator.calculateWeightedScore(List.of())).isZero();
    }

    @Test
    void weightsEachRulesRiskLevel() {
        List<RuleHit> hits = List.of(
                hit(RuleType.VELOCITY, RuleHitStatus.EVALUATED, 60),
                hit(RuleType.GEO, RuleHitStatus.EVALUATED, 80),
                hit(RuleType.BEHAVIORAL_DEVIATION, RuleHitStatus.EVALUATED, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isEqualTo(50);
    }

    @Test
    void leavesSkippedRulesOutOfTheScore() {
        List<RuleHit> hits = List.of(
                hit(RuleType.VELOCITY, RuleHitStatus.EVALUATED, 60),
                hit(RuleType.GEO, RuleHitStatus.SKIPPED_MISSING_DATA, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isEqualTo(60);
    }

    @Test
    void returnsZeroWhenEveryRuleWasSkipped() {
        List<RuleHit> hits = List.of(hit(RuleType.GEO, RuleHitStatus.SKIPPED_MISSING_DATA, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isZero();
    }

    private static RuleHit hit(final RuleType ruleType, final RuleHitStatus status, final int riskLevel) {
        RuleHit hit = new RuleHit();
        hit.setRuleType(ruleType);
        hit.setStatus(status);
        hit.setRiskLevel(riskLevel);
        return hit;
    }
}
