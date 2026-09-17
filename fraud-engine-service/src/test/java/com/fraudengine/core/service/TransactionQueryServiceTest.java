package com.fraudengine.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import com.fraudengine.core.rule.RuleHitStatus;
import com.fraudengine.core.rule.RuleType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);
    private static final String TRANSACTION_ID = "txn-1";

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private RuleHitRepository ruleHitRepository;

    private TransactionQueryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionQueryService(evaluatedTransactionRepository, ruleHitRepository);
    }

    // ========== list() Filter Tests ==========

    @Test
    void shouldFilterOnTheFlaggedColumn_whenTheStatusIsFlagged() {
        when(evaluatedTransactionRepository.search("ACC-1", "VELOCITY", Boolean.TRUE, PAGEABLE)).thenReturn(Page.empty());

        service.list("ACC-1", "VELOCITY", "flagged", PAGEABLE);

        verify(evaluatedTransactionRepository).search("ACC-1", "VELOCITY", Boolean.TRUE, PAGEABLE);
    }

    @Test
    void shouldFilterOnTheFlaggedColumn_whenTheStatusIsClear() {
        when(evaluatedTransactionRepository.search(null, null, Boolean.FALSE, PAGEABLE)).thenReturn(Page.empty());

        service.list(null, null, "CLEAR", PAGEABLE);

        verify(evaluatedTransactionRepository).search(null, null, Boolean.FALSE, PAGEABLE);
    }

    // PENDING isn't something the query can filter on, so it has to fall through as "no filter"
    @Test
    void shouldNotFilter_whenTheStatusIsNotOneItCanQuery() {
        when(evaluatedTransactionRepository.search(null, null, null, PAGEABLE)).thenReturn(Page.empty());

        service.list(null, null, "PENDING", PAGEABLE);

        verify(evaluatedTransactionRepository).search(null, null, null, PAGEABLE);
    }

    // ========== Effective Status Tests ==========

    @Test
    void shouldReportTheEffectiveStatus_whenTransactionsHaveVerdictsAndOverrides() {
        when(evaluatedTransactionRepository.search(null, null, null, PAGEABLE)).thenReturn(new PageImpl<>(List.of(
                transaction("txn-1", true, null),
                transaction("txn-2", true, false),
                transaction("txn-3", false, true),
                transaction("txn-4", null, null))));

        List<EvaluatedTransactionResponse> responses = service.list(null, null, null, PAGEABLE).getContent();

        assertThat(responses).extracting(EvaluatedTransactionResponse::getStatus)
                .containsExactly("FLAGGED", "CLEAR", "FLAGGED", "PENDING");
    }

    // both verdicts are returned, so a caller can see the system was overruled
    @Test
    void shouldKeepBothVerdicts_whenATransactionWasOverridden() {
        when(evaluatedTransactionRepository.search(null, null, null, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(transaction(TRANSACTION_ID, true, false))));

        EvaluatedTransactionResponse response = service.list(null, null, null, PAGEABLE).getContent().get(0);

        assertThat(response.getStatus()).isEqualTo("CLEAR");
        assertThat(response.getFlagged()).isTrue();
        assertThat(response.getOverriddenFlagged()).isFalse();
    }

    // ========== get() Tests ==========

    @Test
    void shouldReturnTheTransactionWithItsRuleHits_whenItExists() {
        when(evaluatedTransactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction(TRANSACTION_ID, false, null)));
        when(ruleHitRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(List.of(hit()));

        TransactionDetailResponse detail = service.get(TRANSACTION_ID);

        assertThat(detail.getTransaction().getStatus()).isEqualTo("CLEAR");
        assertThat(detail.getRuleHits()).hasSize(1);
        assertThat(detail.getRuleHits().get(0).getRuleType()).isEqualTo("VELOCITY");
        assertThat(detail.getRuleHits().get(0).getRiskLevel()).isEqualTo(50);
    }

    // ========== Helper Methods ==========

    private static EvaluatedTransaction transaction(final String id, final Boolean flagged, final Boolean overriddenFlagged) {
        EvaluatedTransaction transaction = new EvaluatedTransaction();
        transaction.setId(id);
        transaction.setAccountNumber("ACC-1");
        transaction.setTransactionType("TRANSFER");
        transaction.setFlagged(flagged);
        transaction.setOverriddenFlagged(overriddenFlagged);
        return transaction;
    }

    private static RuleHit hit() {
        RuleHit hit = new RuleHit();
        hit.setRuleType(RuleType.VELOCITY);
        hit.setStatus(RuleHitStatus.EVALUATED);
        hit.setRiskLevel(50);
        return hit;
    }
}
