package com.fraudengine.core.exception;

public class TransactionNotFoundException extends FraudEngineException {

    public TransactionNotFoundException(final String messageKey) {
        super(messageKey);
    }
}
