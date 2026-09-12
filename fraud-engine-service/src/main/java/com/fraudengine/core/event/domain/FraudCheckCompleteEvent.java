package com.fraudengine.core.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckCompleteEvent {

    private String transactionId;
    private String accountNumber;
    private String transactionType;
    private boolean flagged;
    private int highestRiskLevel;
    private Instant completedAt;
    private List<RuleEvaluation> ruleEvaluations;
    private int weightedRiskScore;
}
