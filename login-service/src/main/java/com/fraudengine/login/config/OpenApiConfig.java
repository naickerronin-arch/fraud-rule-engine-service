package com.fraudengine.login.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenApiConfig.class);

    @Value("${gateway.external-url:not-set}")
    private String gatewayExternalUrl;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @PostConstruct
    public void init() {
        log.info("Setting up OpenApiConfig, gatewayExternalUrl: {}", gatewayExternalUrl);
    }

    @Bean
    public OpenAPI customOpenApi() {
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("Fraud Rule Engine — Login Service")
                        .version("v1")
                        .description("Dev/demo convenience only: wraps the password grant against Dex's local connector."));

        if (gatewayExternalUrl != null && !gatewayExternalUrl.equals("not-set")) {
            openApi = openApi.addServersItem(new Server().url(gatewayExternalUrl + contextPath));
        }

        return openApi;
    }
}
