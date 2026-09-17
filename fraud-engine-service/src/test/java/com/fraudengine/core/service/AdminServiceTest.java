package com.fraudengine.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.exception.TransactionNotFoundException;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final String TRANSACTION_ID = "txn-1";

    @Mock
    private EvaluatedTransactionRepository evaluatedTransactionRepository;

    @Mock
    private BadLocationRepository badLocationRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(evaluatedTransactionRepository, badLocationRepository);
    }

    // ========== overrideTransaction() Tests ==========

    // the override is stored beside the system's verdict, never over it
    @Test
    void shouldRecordTheOverride_whenTheTransactionExists() {
        when(evaluatedTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(new EvaluatedTransaction()));

        TransactionOverrideResponse response =
                adminService.overrideTransaction(TRANSACTION_ID, new OverrideTransactionRequest(true));

        verify(evaluatedTransactionRepository).applyOverride(eq(TRANSACTION_ID), eq(true), any(Instant.class));
        assertThat(response.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.isOverriddenFlagged()).isTrue();
        assertThat(response.getOverriddenAt()).isNotNull();
    }

    @Test
    void shouldReject_whenTheTransactionDoesNotExist() {
        when(evaluatedTransactionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.overrideTransaction("missing", new OverrideTransactionRequest(true)))
                .isInstanceOf(TransactionNotFoundException.class);

        verify(evaluatedTransactionRepository, never()).applyOverride(anyString(), anyBoolean(), any(Instant.class));
    }
}
