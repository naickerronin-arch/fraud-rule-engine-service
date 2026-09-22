package com.fraudengine.gateway.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("app")
public class ApplicationProperties {

    private SecurityConfig securityConfig = new SecurityConfig();

    @Data
    public static class SecurityConfig {
        private List<String> loginPaths = new ArrayList<>();
        private List<String> complianceTeamPaths = new ArrayList<>();
    }
}
