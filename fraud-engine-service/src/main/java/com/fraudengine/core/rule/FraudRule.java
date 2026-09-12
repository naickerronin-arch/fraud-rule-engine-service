package com.fraudengine.core.rule;

import com.fraudengine.core.event.domain.TransactionEvent;

public interface FraudRule {

    RuleType ruleType();

    boolean isEnabledFor(String transactionType);

    RuleResult evaluateRule(TransactionEvent transaction);

    default boolean canFlagStandalone() {
        return true;
    }
}
