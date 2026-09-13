package com.fraudengine.core.metrics;

import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class FraudEngineMetricsAspect {

    private static final String METRIC_RULE_EVALUATED = "fraud.rule.evaluated";
    private static final String METRIC_RULE_DURATION = "fraud.rule.duration";
    private static final String METRIC_FRAUD_CHECK_COMPLETE = "fraud.check.complete";
    private static final String METRIC_TRANSACTION_OVERRIDE = "fraud.transaction.override";
    private static final String METRIC_DLT_RECEIVED = "fraud.dlt.received";

    private final MetricsRecorder metricsRecorder;

    @Around("execution(* com.fraudengine.core.rule.FraudRule+.evaluateRule(..)) && target(rule)")
    public Object recordRuleEvaluation(final ProceedingJoinPoint joinPoint, final FraudRule rule) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof RuleResult ruleResult) {
                metricsRecorder.increment(
                        METRIC_RULE_EVALUATED,
                        "rule", rule.ruleType().name(),
                        "status", ruleResult.status().name(),
                        "flagged", String.valueOf(ruleResult.flagged()));
            }
            return result;
        } finally {
            metricsRecorder.recordDuration(
                    METRIC_RULE_DURATION,
                    Duration.ofNanos(System.nanoTime() - startNanos),
                    "rule", rule.ruleType().name());
        }
    }

    @AfterReturning("execution(* com.fraudengine.core.outbox.OutboxWriter.publish(..)) && args(event)")
    public void recordFraudCheckComplete(final FraudCheckCompleteEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metricsRecorder.increment(METRIC_FRAUD_CHECK_COMPLETE, "flagged", String.valueOf(event.isFlagged()));
            }
        });
    }

    @AfterReturning(
            pointcut = "execution(* com.fraudengine.core.service.AdminService.overrideTransaction(..))",
            returning = "response")
    public void recordOverride(final TransactionOverrideResponse response) {
        metricsRecorder.increment(METRIC_TRANSACTION_OVERRIDE, "flagged", String.valueOf(response.isOverriddenFlagged()));
    }

    @Around("execution(public * com.fraudengine.core.event.consumer.DltAuditListener.handle(..))")
    public Object recordDltReceived(final ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } finally {
            metricsRecorder.increment(METRIC_DLT_RECEIVED);
        }
    }

}
