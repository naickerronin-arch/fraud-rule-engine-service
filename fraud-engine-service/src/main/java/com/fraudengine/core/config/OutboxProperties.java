package com.fraudengine.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("app.outbox")
public class OutboxProperties {

    private long relayIntervalMs = 100;
    private long sendTimeoutMs = 5000;
    private int maxAttempts = 5;
}
