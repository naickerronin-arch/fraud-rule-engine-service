package com.fraudengine.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAspectJAutoProxy
@EnableScheduling
@SpringBootApplication
public final class FraudEngineServiceApplication {

    private FraudEngineServiceApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(FraudEngineServiceApplication.class, args);
    }
}
