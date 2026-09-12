package com.fraudengine.core.exception;

import com.fraudengine.core.util.MessageUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Global exception handler for consistent error responses across all endpoints.
 * Catches common exceptions and transforms them into standardized error responses.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageUtil messageUtil;

    /**
     * Returns HTTP 400 with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(final MethodArgumentNotValidException exception) {
        if (exception.hasFieldErrors()) {
            var fieldErrors = exception.getFieldErrors().stream()
                    .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                    .toList();

            ErrorResponse errorResponse = new ErrorResponse(ErrorResponseType.FIELD_ERROR, fieldErrors);
            LOGGER.error("Validation Exception. {}", errorResponse);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return ResponseEntity.badRequest().build();
    }

    /**
     * Handles type mismatch errors (e.g., passing string where number is expected).
     * Returns HTTP 400 with error details.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchExceptions(
            final MethodArgumentTypeMismatchException exception) {
        LOGGER.error(exception.getMessage());
        var fieldError = new FieldError(exception.getName(), "Required type: " + exception.getRequiredType());
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorResponseType.FIELD_ERROR, List.of(fieldError)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(final ConstraintViolationException ex) {
        var fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(ErrorResponseType.FIELD_ERROR, fieldErrors);
        LOGGER.error("Constraint Violation. {}", errorResponse);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(FraudEngineException.class)
    public ResponseEntity<ErrorResponse> handleFraudEngineException(final FraudEngineException ex) {
        MessageUtil.ErrorDetail error = messageUtil.getError(ex.messageKey());
        LOGGER.error("Fraud Engine error: code={}, message={}, detail={}", error.getCode(), error.getMessage(), ex.getMessage());
        var response = new GenericExceptionResponse(error.getCode(), error.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorResponseType.SERVICE_ERROR, List.of(response)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleCatchAllException(final Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        MessageUtil.ErrorDetail error = messageUtil.getError(FraudEngineErrorMessages.ERROR_GENERIC_INTERNAL);
        var response = new GenericExceptionResponse(error.getCode(), error.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorResponseType.SERVICE_ERROR, List.of(response)));
    }
}
