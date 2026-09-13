package com.fraudengine.core.rule;

import com.fraudengine.core.event.domain.TransactionEvent;

public interface FraudRule {

    RuleType ruleType();

    boolean isEnabledFor(String transactionType);

    RuleResult evaluateRule(TransactionEvent transaction);

    default boolean canFlagStandalone() {
        return true;// certain rules should be able to flag a transaction for fraud with only their data, others form part of the weighting score
    }
}
