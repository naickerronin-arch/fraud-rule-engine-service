package com.fraudengine.core.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageUtil {

    private final MessageSource messageSource;

    public ErrorDetail getError(final String messageKey) {
        try {
            String message = messageSource.getMessage(messageKey, null, Locale.ENGLISH);
            String[] parts = message.split("\\|", 2);
            if (parts.length == 2) {
                return new ErrorDetail(parts[0].trim(), parts[1].trim());
            }
            return new ErrorDetail("999", message);
        } catch (NoSuchMessageException e) {
            log.warn("Message key not found: {}", messageKey);
            return new ErrorDetail("999", "An unexpected error occurred");
        }
    }

    @Data
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}
