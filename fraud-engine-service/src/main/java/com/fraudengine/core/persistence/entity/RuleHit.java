package com.fraudengine.core.persistence.entity;

import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "rule_hits")
@Getter
@Setter
@NoArgsConstructor
public class RuleHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String transactionId;
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;
    @Enumerated(EnumType.STRING)
    private RuleHitStatus status;
    private boolean flagged;
    private int riskLevel;
    private Instant evaluatedAt;
}
