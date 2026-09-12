package com.fraudengine.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Table(name = "evaluated_transactions")
@NoArgsConstructor
@Data
public class EvaluatedTransaction {

    @Id
    private String id;
    @Column(name = "account_id")
    private String accountNumber;
    private BigDecimal amount;
    private String transactionType;
    private String areaCode;
    private Instant createdAt;
    private Boolean flagged;
    private Boolean overriddenFlagged;
    private String overriddenBy;
    private Instant overriddenAt;
    private String overrideReason;
}
