package com.fraudengine.gateway.error;

import java.time.Instant;

public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String traceId,
        Instant timestamp
) {
}
