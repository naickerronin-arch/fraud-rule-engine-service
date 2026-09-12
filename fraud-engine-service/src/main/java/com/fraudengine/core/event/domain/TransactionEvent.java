package com.fraudengine.core.event.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class TransactionEvent {

    @NotNull
    private String transactionId;
    @NotNull
    private String accountNumber;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private Instant timestamp;
    @NotNull
    private String transactionType;

    private String beneficiaryAccountNumber;
    private String beneficiaryBranchCode;
    private String areaCode;
}
