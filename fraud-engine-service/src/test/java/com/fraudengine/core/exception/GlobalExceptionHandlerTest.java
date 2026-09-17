package com.fraudengine.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudengine.core.util.MessageUtil;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage(FraudEngineErrorMessages.TRANSACTION_NOT_FOUND, Locale.ENGLISH, "001|Transaction not found");
        messageSource.addMessage(FraudEngineErrorMessages.LIST_TRANSACTIONS_FAILED, Locale.ENGLISH, "012|Failed to list transactions");
        messageSource.addMessage(FraudEngineErrorMessages.ERROR_GENERIC_INTERNAL, Locale.ENGLISH, "999|An unexpected error occurred");

        handler = new GlobalExceptionHandler(new MessageUtil(messageSource));
    }

    // ========== FraudEngineException Tests ==========

    @Test
    void shouldReturnNotFound_whenTheTransactionIsMissing() {
        ResponseEntity<ErrorResponse> response = handler.handleTransactionNotFoundException(
                new TransactionNotFoundException(FraudEngineErrorMessages.TRANSACTION_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getType()).isEqualTo(ErrorResponseType.SERVICE_ERROR.name());
        assertThat(onlyError(response)).isEqualTo(new GenericExceptionResponse("001", "Transaction not found"));
    }

    @Test
    void shouldReturnServerErrorWithTheRulesOwnCode_whenAnotherEngineFailureIsThrown() {
        ResponseEntity<ErrorResponse> response = handler.handleFraudEngineException(
                new ListTransactionsException(FraudEngineErrorMessages.LIST_TRANSACTIONS_FAILED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(onlyError(response)).isEqualTo(new GenericExceptionResponse("012", "Failed to list transactions"));
    }

    // ========== Catch-All Tests ==========

    // internal failures never leak their message to the caller
    @Test
    void shouldHideTheDetail_whenAnUnexpectedExceptionIsThrown() {
        ResponseEntity<ErrorResponse> response = handler.handleCatchAllException(new IllegalStateException("connection reset"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(onlyError(response)).isEqualTo(new GenericExceptionResponse("999", "An unexpected error occurred"));
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private static GenericExceptionResponse onlyError(final ResponseEntity<ErrorResponse> response) {
        List<GenericExceptionResponse> errors = (List<GenericExceptionResponse>) response.getBody().getErrors();
        assertThat(errors).hasSize(1);
        return errors.get(0);
    }
}
