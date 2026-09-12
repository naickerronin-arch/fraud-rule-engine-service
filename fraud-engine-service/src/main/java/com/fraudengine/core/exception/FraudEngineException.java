package com.fraudengine.core.exception;

public abstract class FraudEngineException extends RuntimeException {

    private final String messageKey;

    protected FraudEngineException(final String errorMessageKey) {
        super(errorMessageKey);
        this.messageKey = errorMessageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
