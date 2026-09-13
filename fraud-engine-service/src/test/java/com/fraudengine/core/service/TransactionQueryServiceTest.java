package com.fraudengine.core.service;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionQueryServiceTest {

    private final Pageable pageable = PageRequest.of(0, 20);

    private EvaluatedTransactionRepository evaluatedTransactionRepository;
    private RuleHitRepository ruleHitRepository;
    private TransactionQueryService service;

    @BeforeEach
    void setUp() {
        evaluatedTransactionRepository = mock(EvaluatedTransactionRepository.class);
        ruleHitRepository = mock(RuleHitRepository.class);
        service = new TransactionQueryService(evaluatedTransactionRepository, ruleHitRepository);
        when(evaluatedTransactionRepository.search(any(), any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    void filtersFlaggedTransactions() {
        service.list("ACC-1", "VELOCITY", "flagged", pageable);

        verify(evaluatedTransactionRepository).search("ACC-1", "VELOCITY", Boolean.TRUE, pageable);
    }

    @Test
    void filtersClearTransactions() {
        service.list(null, null, "CLEAR", pageable);

        verify(evaluatedTransactionRepository).search(null, null, Boolean.FALSE, pageable);
    }

    @Test
    void returnsTheEffectiveStatusOfEachTransaction() {
        EvaluatedTransaction flagged = transaction("txn-1", true, null);
        EvaluatedTransaction overriddenToClear = transaction("txn-2", true, false);
        EvaluatedTransaction pending = transaction("txn-3", null, null);
        when(evaluatedTransactionRepository.search(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(flagged, overriddenToClear, pending)));

        List<EvaluatedTransactionResponse> responses = service.list(null, null, null, pageable).getContent();

        assertThat(responses).extracting(EvaluatedTransactionResponse::getStatus)
                .containsExactly("FLAGGED", "CLEAR", "PENDING");
        assertThat(responses.get(1).getFlagged()).isTrue();
        assertThat(responses.get(1).getOverriddenFlagged()).isFalse();
    }

    @Test
    void returnsTheTransactionWithItsRuleHits() {
        when(evaluatedTransactionRepository.findById("txn-1")).thenReturn(Optional.of(transaction("txn-1", false, null)));
        RuleHit hit = new RuleHit();
        hit.setRuleType(RuleType.VELOCITY);
        hit.setStatus(RuleHitStatus.EVALUATED);
        hit.setRiskLevel(50);
        when(ruleHitRepository.findByTransactionId("txn-1")).thenReturn(List.of(hit));

        TransactionDetailResponse detail = service.get("txn-1");

        assertThat(detail.getTransaction().getStatus()).isEqualTo("CLEAR");
        assertThat(detail.getRuleHits()).hasSize(1);
        assertThat(detail.getRuleHits().get(0).getRuleType()).isEqualTo("VELOCITY");
        assertThat(detail.getRuleHits().get(0).getRiskLevel()).isEqualTo(50);
    }

    private static EvaluatedTransaction transaction(final String id, final Boolean flagged, final Boolean overriddenFlagged) {
        EvaluatedTransaction transaction = new EvaluatedTransaction();
        transaction.setId(id);
        transaction.setAccountNumber("ACC-1");
        transaction.setTransactionType("TRANSFER");
        transaction.setFlagged(flagged);
        transaction.setOverriddenFlagged(overriddenFlagged);
        return transaction;
    }
}
