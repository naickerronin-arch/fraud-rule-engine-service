package com.fraudengine.login.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RestClientConfiguration {

    private final ApplicationProperties applicationProperties;

    @Bean
    public RestClient dexRestClient(){
        return RestClient.builder().baseUrl(applicationProperties.getDexConfig().getBaseUrl()).build();
    }

}
