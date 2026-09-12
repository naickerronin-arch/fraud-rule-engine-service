package com.fraudengine.core.metrics;

import com.fraudengine.core.rule.FraudRule;
import com.fraudengine.core.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class FraudEngineMetricsAspect {

    private static final String METRIC_RULE_EVALUATED = "fraud.rule.evaluated";
    private static final String METRIC_RULE_DURATION = "fraud.rule.duration";
    private static final String METRIC_DLT_RECEIVED = "fraud.dlt.received";

    private final MetricsRecorder metricsRecorder;

    @Around("execution(public * com.fraudengine.core.rule.strategy.*.evaluate(..)) && target(rule)")
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

    @Around("execution(public * com.fraudengine.core.event.consumer.DltAuditListener.handle(..))")
    public Object recordDltReceived(final ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } finally {
            metricsRecorder.increment(METRIC_DLT_RECEIVED);
        }
    }

}
