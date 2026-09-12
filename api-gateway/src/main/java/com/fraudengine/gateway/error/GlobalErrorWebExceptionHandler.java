package com.fraudengine.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        String traceId = exchange.getAttributeOrDefault("traceId", "n/a");

        ErrorResponse body = new ErrorResponse(
                "https://fraudengine.dev/errors/" + status.value(),
                status.getReasonPhrase(),
                status.value(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                traceId,
                Instant.now()
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        return exchange.getResponse().writeWith(Mono.fromSupplier(() -> {
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(body);
                return exchange.getResponse().bufferFactory().wrap(bytes);
            } catch (Exception e) {
                DataBuffer fallback = exchange.getResponse().bufferFactory()
                        .wrap(("{\"detail\":\"" + status.getReasonPhrase() + "\"}")
                                .getBytes(StandardCharsets.UTF_8));
                return fallback;
            }
        }));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof InvalidBearerTokenException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof CallNotPermittedException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
