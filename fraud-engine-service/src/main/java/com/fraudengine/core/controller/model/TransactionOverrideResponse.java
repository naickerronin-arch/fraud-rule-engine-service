package com.fraudengine.core.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionOverrideResponse {

    private String transactionId;
    private boolean overriddenFlagged;
    private String overriddenBy;
    private Instant overriddenAt;
    private String overrideReason;
}
