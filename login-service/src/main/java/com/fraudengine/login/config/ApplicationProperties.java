package com.fraudengine.login.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
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

    private DexConfig dexConfig = new DexConfig();

    @Data
    public static class DexConfig {
        private String baseUrl;
        private String clientSecret;
        private String clientId;
    }


}

