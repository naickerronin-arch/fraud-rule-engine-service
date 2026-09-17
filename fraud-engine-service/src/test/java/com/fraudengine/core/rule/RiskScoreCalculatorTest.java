package com.fraudengine.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudengine.core.config.ApplicationProperties;
import com.fraudengine.core.persistence.entity.RuleHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    // ========== calculateWeightedScore() Tests ==========

    @Test
    void shouldReturnZero_whenThereAreNoHits() {
        assertThat(calculator.calculateWeightedScore(null)).isZero();
        assertThat(calculator.calculateWeightedScore(List.of())).isZero();
    }

    @Test
    void shouldWeightEachRulesRiskLevel_whenEveryRuleEvaluated() {
        List<RuleHit> hits = List.of(
                hit(RuleType.VELOCITY, RuleHitStatus.EVALUATED, 60),
                hit(RuleType.GEO, RuleHitStatus.EVALUATED, 80),
                hit(RuleType.BEHAVIORAL_DEVIATION, RuleHitStatus.EVALUATED, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isEqualTo(50);
    }

    // the score is normalised over the rules that ran, so a skipped rule can't cap it
    @Test
    void shouldNormaliseOverTheRemainingWeights_whenARuleWasSkipped() {
        List<RuleHit> hits = List.of(
                hit(RuleType.VELOCITY, RuleHitStatus.EVALUATED, 60),
                hit(RuleType.GEO, RuleHitStatus.SKIPPED_MISSING_DATA, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isEqualTo(60);
    }

    @Test
    void shouldReturnZero_whenEveryRuleWasSkipped() {
        List<RuleHit> hits = List.of(hit(RuleType.GEO, RuleHitStatus.SKIPPED_MISSING_DATA, 0));

        assertThat(calculator.calculateWeightedScore(hits)).isZero();
    }

    // ========== Helper Methods ==========

    private static RuleHit hit(final RuleType ruleType, final RuleHitStatus status, final int riskLevel) {
        RuleHit hit = new RuleHit();
        hit.setRuleType(ruleType);
        hit.setStatus(status);
        hit.setRiskLevel(riskLevel);
        return hit;
    }
}
