package com.fraudengine.core.event.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "\\d{4}", message = "must be a four digit postal code")
    private String areaCode;
}
