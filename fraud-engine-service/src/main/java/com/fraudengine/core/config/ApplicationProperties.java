package com.fraudengine.core.config;

import com.fraudengine.core.rule.RuleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("app")
public class ApplicationProperties {

    private int overallFlagThreshold = 50;
    private JpaSettings jpaSettings = new JpaSettings();
    private KafkaSettings kafkaSettings = new KafkaSettings();
    private Map<String, TransactionTypeConfig> transactionTypes = new HashMap<>();
    private VelocityConfig velocityConfig = new VelocityConfig();
    private LocationConfig locationConfig = new LocationConfig();
    private BehavioralDeviationConfig behavioralDeviationConfig = new BehavioralDeviationConfig();

    @Data
    public static class TransactionTypeConfig {
        private List<RuleType> enabledRules = new ArrayList<>(); // sets enabled rules per transaction type
    }

    @Data
    public abstract static class BaseRuleConfig {
        private double weight = 0.33; // default rule weighting
        private int maxRiskLevel = 100;
    }

    @Data
    public static class VelocityConfig extends BaseRuleConfig {
        private int windowMinutes = 10;
        private int defaultMaxTransactions = 5;
        private int minHistoryCount = 20;
        private int transactionWindow = 30;
        private double multiplier = 3;
        private double alertThreshold = 2.0; // if rule violates twice the norm, the warn for fraud
    }

    @Data
    public static class LocationConfig extends BaseRuleConfig {
        private int minTransactionCount = 20;
        private double levelOneThresholdPercent = 30;
        private double levelTwoThresholdPercent = 50;
        private double levelThreeThresholdPercent = 80;
        private int levelOneRiskLevel = 30;
        private int levelTwoRiskLevel = 55;
        private int levelThreeRiskLevel = 80;
    }

    @Data
    public static class BehavioralDeviationConfig extends BaseRuleConfig {
        private int minHistoryCount = 20;
        private double stdDevThreshold = 3;
        private double alertThreshold = 5.0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JpaSettings {
        private String physicalNamingStrategy = "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy";
        private String dialect = "org.hibernate.dialect.PostgreSQLDialect";
        private Map<String, String> properties = new HashMap<>();
    }

    @Data
    public static class KafkaSettings {
        private int consumerConcurrency = 6;
        private int retryAttempts = 3;
        private long retryInitialIntervalMs = 500L;
        private double retryBackoffMultiplier = 2.0;
    }
}
