package com.fraudengine.core.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String type;

    private List<?> errors;

    public ErrorResponse(final ErrorResponseType errorResponseType, final List<?> errorList) {
        this.type = errorResponseType.name();
        this.errors = errorList;
    }
}
