package com.fraudengine.core.rule;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@RequiredArgsConstructor
@Builder
public final class RuleResult {

    private final RuleHitStatus status;
    private final boolean flagged;
    private final int riskLevel;
}
