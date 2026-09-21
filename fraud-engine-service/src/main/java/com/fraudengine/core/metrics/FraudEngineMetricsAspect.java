package com.fraudengine.core.metrics;

import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.event.domain.FraudCheckCompleteEvent;
import com.fraudengine.core.event.domain.FraudCheckFailedEvent;
import com.fraudengine.core.event.handler.PendingEvaluationSweeper;
import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleResult;
import com.fraudengine.core.rule.RuleType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
public class FraudEngineMetricsAspect {

    private static final String METRIC_RULE_EVALUATED = "fraud.rule.evaluated";
    private static final String METRIC_RULE_DURATION = "fraud.rule.duration";
    private static final String METRIC_FRAUD_CHECK_COMPLETE = "fraud.check.complete";
    private static final String METRIC_TRANSACTION_OVERRIDE = "fraud.transaction.override";
    private static final String METRIC_FRAUD_CHECK_FAILED = "fraud.check.failed";
    private static final String METRIC_DLT_RECEIVED = "fraud.dlt.received";
    private static final String STATUS_ERROR = "ERROR";

    private final MetricsRecorder metricsRecorder;

    @PostConstruct
    void registerCounters() {
        for (String flagged : List.of("true", "false")) {
            for (RuleType rule : RuleType.values()) {
                metricsRecorder.register(METRIC_RULE_EVALUATED,
                        "rule", rule.name(), "status", RuleHitStatus.EVALUATED.name(), "flagged", flagged);
            }
            metricsRecorder.register(METRIC_FRAUD_CHECK_COMPLETE, "flagged", flagged);
            metricsRecorder.register(METRIC_TRANSACTION_OVERRIDE, "flagged", flagged);
        }
        for (String reason : List.of(PendingEvaluationSweeper.RULES_DID_NOT_REPORT,
                PendingEvaluationSweeper.TRANSACTION_TYPE_NOT_SUPPORTED)) {
            metricsRecorder.register(METRIC_FRAUD_CHECK_FAILED, "reason", reason);
        }
        metricsRecorder.register(METRIC_DLT_RECEIVED);
    }

    @Around("execution(* com.fraudengine.core.rule.FraudRule+.evaluateRule(..)) && target(rule)")
    public Object recordRuleEvaluation(final ProceedingJoinPoint joinPoint, final FraudRule rule) throws Throwable {
        long startNanos = System.nanoTime();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            recordRuleEvaluated(rule, STATUS_ERROR, false);
            throw e;
        } finally {
            metricsRecorder.recordDuration(
                    METRIC_RULE_DURATION,
                    Duration.ofNanos(System.nanoTime() - startNanos),
                    "rule", rule.ruleType().name());
        }

        if (result instanceof RuleResult ruleResult) {
            recordRuleEvaluated(rule, ruleResult.status().name(), ruleResult.flagged());
        }
        return result;
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

    @AfterReturning("execution(* com.fraudengine.core.outbox.OutboxWriter.publish(..)) && args(event)")
    public void recordFraudCheckFailed(final FraudCheckFailedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metricsRecorder.increment(METRIC_FRAUD_CHECK_FAILED, "reason", event.getReason());
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

    private void recordRuleEvaluated(final FraudRule rule, final String status, final boolean flagged) {
        metricsRecorder.increment(
                METRIC_RULE_EVALUATED,
                "rule", rule.ruleType().name(),
                "status", status,
                "flagged", String.valueOf(flagged));
    }

}
