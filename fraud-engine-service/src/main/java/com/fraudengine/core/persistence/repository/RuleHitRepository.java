package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.RuleHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RuleHitRepository extends JpaRepository<RuleHit, Long> {

    @Modifying
    @Query(
            value = """
                INSERT INTO rule_hits
                (transaction_id, rule_type, status, flagged, risk_level, evaluated_at)
                VALUES (:transactionId, :ruleType, :status, :flagged, :riskLevel, :evaluatedAt)
                ON CONFLICT (transaction_id, rule_type) DO NOTHING
                """,
            nativeQuery = true)
    void upsert(
            @Param("transactionId") String transactionId,
            @Param("ruleType") String ruleType,
            @Param("status") String status,
            @Param("flagged") boolean flagged,
            @Param("riskLevel") int riskLevel,
            @Param("evaluatedAt") Instant evaluatedAt); // ensure rule is only processed once


    long countByTransactionId(String transactionId);

    List<RuleHit> findByTransactionId(String transactionId);
}
