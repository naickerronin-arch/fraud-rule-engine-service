package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluatedTransactionRepository extends JpaRepository<EvaluatedTransaction, String> {
    @Modifying
    @Query(
            value = """
                INSERT INTO evaluated_transactions
                (id, account_id, amount, transaction_type, area_code, event_time)
                VALUES (:id, :accountNumber, :amount, :transactionType, :areaCode, :eventTime)
                ON CONFLICT (id) DO NOTHING
                """,
            nativeQuery = true)
    void upsert(
            @Param("id") String id,
            @Param("accountNumber") String accountNumber,
            @Param("amount") BigDecimal amount,
            @Param("transactionType") String transactionType,
            @Param("areaCode") String areaCode,
            @Param("eventTime") Instant eventTime);

    // capped at the transaction being judged, so a rule that falls behind doesn't count what came after it
    @Query("""
            SELECT COUNT(t) FROM EvaluatedTransaction t
            WHERE t.accountNumber = :accountNumber
              AND t.eventTime > :from
              AND t.eventTime <= :to
            """)
    long countInWindow(@Param("accountNumber") String accountNumber, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            value = """
                SELECT count(*)
                FROM evaluated_transactions
                WHERE account_id = :accountNumber
                  AND event_time > :from
                  AND event_time <= :to
                GROUP BY date_bin(:windowMinutes * interval '1 minute', event_time, timestamp '2000-01-01')
                HAVING count(*) FILTER (WHERE COALESCE(overridden_flagged, flagged)) = 0
                ORDER BY count(*) DESC
                LIMIT 1
                """,
            nativeQuery = true)
    Optional<Long> findBusiestWindowCount(@Param("accountNumber") String accountNumber, @Param("from") Instant from,
                                          @Param("to") Instant to, @Param("windowMinutes") int windowMinutes);

    long countByAccountNumber(String accountNumber);

    long countByFlaggedIsNullAndAbandonedAtIsNull();

    long countByAbandonedAtIsNotNull();

    // rows a rule is mid-completion on are locked, so SKIP LOCKED leaves them to finish
    @Query(
            value = """
                SELECT * FROM evaluated_transactions
                WHERE flagged IS NULL
                  AND abandoned_at IS NULL
                  AND created_at < :cutoff
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """,
            nativeQuery = true)
    List<EvaluatedTransaction> findAwaitingVerdictBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Modifying
    @Query(
            value = """
                UPDATE evaluated_transactions
                SET abandoned_at = :abandonedAt
                WHERE id = :id
                """,
            nativeQuery = true)
    void markAbandoned(@Param("id") String id, @Param("abandonedAt") Instant abandonedAt);

    @Query(
            value = """
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE COALESCE(overridden_flagged, flagged)) AS flagged
                FROM evaluated_transactions
                WHERE area_code = :areaCode
                  AND COALESCE(overridden_flagged, flagged) IS NOT NULL
                """,
            nativeQuery = true)
    AreaStats findAreaStats(@Param("areaCode") String areaCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EvaluatedTransaction> findLockedById(String id);

    @Modifying
    @Query(
            value = """
                UPDATE evaluated_transactions
                SET overridden_flagged = :overriddenFlagged,
                    overridden_at = :overriddenAt
                WHERE id = :id
                """,
            nativeQuery = true)
    void applyOverride(
            @Param("id") String id,
            @Param("overriddenFlagged") boolean overriddenFlagged,
            @Param("overriddenAt") Instant overriddenAt);

    @Query(
            value = """
                SELECT AVG(amount) AS avg_amount,
                       STDDEV_SAMP(amount) AS std_dev_amount,
                       COUNT(*) AS sample_count
                FROM evaluated_transactions
                WHERE account_id = :accountNumber
                  AND id <> :transactionId
                """,
            nativeQuery = true)
    AmountStats findAmountStatsByAccountNumber(
            @Param("accountNumber") String accountNumber,
            @Param("transactionId") String transactionId);

    @Query(
            value = """
                SELECT AVG(amount) AS avg_amount,
                       STDDEV_SAMP(amount) AS std_dev_amount,
                       COUNT(*) AS sample_count
                FROM evaluated_transactions
                WHERE transaction_type = :transactionType
                  AND id <> :transactionId
                """,
            nativeQuery = true)
    AmountStats findAmountStatsByTransactionType(
            @Param("transactionType") String transactionType,
            @Param("transactionId") String transactionId);
    @Query(
            value = """
                SELECT et.* FROM evaluated_transactions et
                WHERE (:accountNumber IS NULL OR et.account_id = :accountNumber)
                  AND (:ruleType IS NULL OR EXISTS (
                    SELECT 1 FROM rule_hits rh
                    WHERE rh.transaction_id = et.id AND rh.rule_type = :ruleType
                  ))
                  AND (:flagged IS NULL OR COALESCE(et.overridden_flagged, et.flagged) = :flagged)
                ORDER BY et.event_time DESC
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

    interface AreaStats {
        Long getTotal();

        Long getFlagged();
    }
}
