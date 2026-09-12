package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface EvaluatedTransactionRepository extends JpaRepository<EvaluatedTransaction, String> {
    @Modifying
    @Query(
            value = """
                INSERT INTO evaluated_transactions
                (id, account_id, amount, transaction_type, area_code, created_at)
                VALUES (:id, :accountNumber, :amount, :transactionType, :areaCode, :createdAt)
                ON CONFLICT (id) DO NOTHING
                """,
            nativeQuery = true)
    void upsert(
            @Param("id") String id,
            @Param("accountNumber") String accountNumber,
            @Param("amount") BigDecimal amount,
            @Param("transactionType") String transactionType,
            @Param("areaCode") String areaCode,
            @Param("createdAt") Instant createdAt);

    long countByAccountNumberAndCreatedAtAfter(String accountNumber, Instant since);

    long countByAccountNumber(String accountNumber);

    long countByAreaCode(String areaCode);

    @Query(
            value = """
                SELECT COUNT(*) FROM evaluated_transactions
                WHERE area_code = :areaCode AND COALESCE(overridden_flagged, flagged) = true
                """,
            nativeQuery = true)
    long countEffectiveFlaggedByAreaCode(@Param("areaCode") String areaCode);

    // A claim, not a plain update — "AND flagged IS NULL" makes this atomic across
    // the 3 independent rule-consumers racing to detect completion for the same
    // transaction. Exactly one concurrent caller affects 1 row (wins, should
    // publish); every other caller affects 0 (lost, someone else already
    // published) — removing the "may fire more than once" race entirely, rather
    // than just narrowing its window.
    @Modifying
    @Query(
            value = "UPDATE evaluated_transactions SET flagged = :flagged WHERE id = :id AND flagged IS NULL",
            nativeQuery = true)
    int claimCompletion(@Param("id") String id, @Param("flagged") boolean flagged);

    @Modifying
    @Query(
            value = """
                UPDATE evaluated_transactions
                SET overridden_flagged = :overriddenFlagged,
                    overridden_by = :overriddenBy,
                    overridden_at = :overriddenAt,
                    override_reason = :overrideReason
                WHERE id = :id
                """,
            nativeQuery = true)
    void applyOverride(
            @Param("id") String id,
            @Param("overriddenFlagged") boolean overriddenFlagged,
            @Param("overriddenBy") String overriddenBy,
            @Param("overriddenAt") Instant overriddenAt,
            @Param("overrideReason") String overrideReason);

    @Query(
            value = """
                SELECT AVG(amount) AS avgAmount,
                       STDDEV_SAMP(amount) AS stdDevAmount,
                       COUNT(*) AS sampleCount
                FROM evaluated_transactions
                WHERE account_id = :accountNumber
                """,
            nativeQuery = true)
    AmountStats findAmountStatsByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query(
            value = """
                SELECT AVG(amount) AS avgAmount,
                       STDDEV_SAMP(amount) AS stdDevAmount,
                       COUNT(*) AS sampleCount
                FROM evaluated_transactions
                WHERE transaction_type = :transactionType
                """,
            nativeQuery = true)
    AmountStats findAmountStatsByTransactionType(@Param("transactionType") String transactionType);
    @Query(
            value = """
                SELECT et.* FROM evaluated_transactions et
                WHERE (:accountNumber IS NULL OR et.account_id = :accountNumber)
                  AND (:ruleType IS NULL OR EXISTS (
                    SELECT 1 FROM rule_hits rh
                    WHERE rh.transaction_id = et.id AND rh.rule_type = :ruleType
                  ))
                  AND (:flagged IS NULL OR COALESCE(et.overridden_flagged, et.flagged) = :flagged)
                ORDER BY et.created_at DESC
                """,
            countQuery = """
                SELECT count(*) FROM evaluated_transactions et
                WHERE (:accountNumber IS NULL OR et.account_id = :accountNumber)
                  AND (:ruleType IS NULL OR EXISTS (
                    SELECT 1 FROM rule_hits rh
                    WHERE rh.transaction_id = et.id AND rh.rule_type = :ruleType
                  ))
                  AND (:flagged IS NULL OR COALESCE(et.overridden_flagged, et.flagged) = :flagged)
                """,
            nativeQuery = true)
    Page<EvaluatedTransaction> search(
            @Param("accountNumber") String accountNumber,
            @Param("ruleType") String ruleType,
            @Param("flagged") Boolean flagged,
            Pageable pageable);

    interface AmountStats {
        BigDecimal getAvgAmount();

        BigDecimal getStdDevAmount();

    }
}
