package com.fraudengine.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final String requestId = UUID.randomUUID().toString();

        if (log.isTraceEnabled()) {
            log.trace(
                    "Incoming request: requestId={}, method={}, uri={}, headers={}, params={}",
                    requestId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI(),
                    exchange.getRequest().getHeaders(),
                    exchange.getRequest().getQueryParams());
        }

        long startTime = System.currentTimeMillis();

        // Let the chain carry on
        return chain.filter(exchange).doOnSuccess(_ -> {
            // Log the outgoing response
            HttpStatusCode responseStatus = exchange.getResponse().getStatusCode();
            long elapsedTime = System.currentTimeMillis() - startTime;

            if (log.isTraceEnabled()) {
                log.trace(
                        "Outgoing response: requestId={}, responseStatus={}, uri={} ({} ms)",
                        requestId,
                        responseStatus,
                        exchange.getRequest().getURI(),
                        exchange.getRequest().getHeaders(),
                        elapsedTime);
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
