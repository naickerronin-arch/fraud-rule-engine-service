package com.fraudengine.core.service;

import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    @Test
    void overridesTheVerdict() {
        EvaluatedTransactionRepository repository = mock(EvaluatedTransactionRepository.class);
        when(repository.findById("txn-1")).thenReturn(Optional.of(new EvaluatedTransaction()));
        AdminService service = new AdminService(repository, mock(BadLocationRepository.class));

        TransactionOverrideResponse response = service.overrideTransaction("txn-1", new OverrideTransactionRequest(true));

        verify(repository).applyOverride(eq("txn-1"), eq(true), any(Instant.class));
        assertThat(response.getTransactionId()).isEqualTo("txn-1");
        assertThat(response.isOverriddenFlagged()).isTrue();
        assertThat(response.getOverriddenAt()).isNotNull();
    }
}
