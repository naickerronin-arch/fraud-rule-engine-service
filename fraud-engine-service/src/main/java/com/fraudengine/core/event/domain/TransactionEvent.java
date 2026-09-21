package com.fraudengine.core.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class TransactionEvent {

    @NotBlank
    @Size(max = 64)
    private String transactionId;
    @NotBlank
    @Size(max = 64)
    private String accountNumber;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotNull
    private Instant timestamp;
    @NotBlank
    private String transactionType;

    private String beneficiaryAccountNumber;
    private String beneficiaryBranchCode;
    @Pattern(regexp = "\\d{4}", message = "must be a four digit postal code")
    private String areaCode;
}
