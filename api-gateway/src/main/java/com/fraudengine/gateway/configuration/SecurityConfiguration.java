package com.fraudengine.gateway.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

@Configuration
@Slf4j
@EnableWebFluxSecurity
public class SecurityConfiguration {

    private static final String[] ROOT = {"/"};
    private static final String[] LOGIN_ROUTES = {
            "/dexLogin/**"
    };
    private static final String[] ACTUATOR_ROUTES = {
            "/actuator/refresh",
            "/actuator/gateway/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/metrics/**",
            "/actuator/prometheus/**",
            "/*/actuator/health",
            "/*/actuator/health/**",
            "/*/actuator/metrics/**",
            "/*/actuator/prometheus/**"
    };
    private static final String[] SWAGGER_ROUTES = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/*/swagger-ui/**",
            "/*/swagger-ui.html",
            "/*/v3/api-docs/**",
            "/*/webjars/**",
            "/*/swagger-ui/config",
            "/*/*/swagger-ui/config",
            "/*/*/*/swagger-ui/config/**"
    };
    private static final String[] COMPLIANCE_TEAM = {
            "/fraud-service/admin/transactions/*/override"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        log.debug("Configuring server security....");
        http.authorizeExchange(authorize -> authorize
                        .pathMatchers(ROOT)
                        .permitAll()
                        .pathMatchers(ACTUATOR_ROUTES)
                        .permitAll()
                        .pathMatchers(SWAGGER_ROUTES)
                        .permitAll()
                        .pathMatchers(LOGIN_ROUTES)
                        .permitAll()
                        .pathMatchers(HttpMethod.POST,COMPLIANCE_TEAM)
                        .hasRole("ComplianceTeam")
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oAuth2ResourceServerSpec ->
                        oAuth2ResourceServerSpec.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    private ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String clientId = jwt.getAudience().isEmpty() ? null : jwt.getAudience().get(0);
            String role = ClientRoles.forClientId(clientId);
            List<GrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return Flux.fromIterable(authorities);
        });
        return converter;
    }
}
