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
public class FraudCheckFailedEvent {

    private String transactionId;
    private String accountNumber;
    private String transactionType;
    private String reason;
    private List<String> missingRules;
    private List<RuleEvaluation> ruleEvaluations;
    private Instant firstSeenAt;
    private Instant failedAt;
}
