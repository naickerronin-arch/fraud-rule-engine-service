package com.fraudengine.core.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluatedTransactionResponse {

    private String transactionId;
    private String accountNumber;
    private BigDecimal amount;
    private String transactionType;
    private String areaCode;
    private Instant createdAt;
}
