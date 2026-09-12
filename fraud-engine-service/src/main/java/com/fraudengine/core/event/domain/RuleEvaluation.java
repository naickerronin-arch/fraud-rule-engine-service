package com.fraudengine.core.event.domain;

import com.fraudengine.core.rule.RuleHitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvaluation {

    private String ruleType;
    private RuleHitStatus status;
    private boolean flagged;
    private int riskLevel;
}
