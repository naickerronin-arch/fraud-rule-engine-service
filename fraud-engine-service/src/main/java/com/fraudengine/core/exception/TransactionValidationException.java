package com.fraudengine.core.exception;

public class TransactionValidationException extends FraudEngineException {

    public TransactionValidationException(final String messageKey) {
        super(messageKey);
    }
}
