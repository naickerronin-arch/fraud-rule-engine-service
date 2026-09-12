package com.fraudengine.core.controller.model;

import com.fraudengine.core.rule.RuleHitStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleHitResponse {

    private String ruleType;
    private RuleHitStatus status;
    private boolean flagged;
    private int riskLevel;
    private Instant evaluatedAt;
}
