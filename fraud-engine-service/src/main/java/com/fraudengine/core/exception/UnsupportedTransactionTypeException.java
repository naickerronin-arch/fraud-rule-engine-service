package com.fraudengine.core.exception;

public class UnsupportedTransactionTypeException extends FraudEngineException {

    public UnsupportedTransactionTypeException(final String messageKey) {
        super(messageKey);
    }
}
