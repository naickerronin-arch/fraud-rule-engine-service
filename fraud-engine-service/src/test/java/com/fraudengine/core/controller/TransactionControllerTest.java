package com.fraudengine.core.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import com.fraudengine.core.exception.FraudEngineErrorMessages;
import com.fraudengine.core.exception.GlobalExceptionHandler;
import com.fraudengine.core.exception.TransactionNotFoundException;
import com.fraudengine.core.service.TransactionQueryService;
import com.fraudengine.core.util.MessageUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionQueryService transactionQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionQueryService))
                .setControllerAdvice(new GlobalExceptionHandler(new MessageUtil(messageSource)))
                .build();
    }

    // ========== list() Tests ==========

    @Test
    void shouldPassEveryFilterToTheService_whenTheyAreGiven() throws Exception {
        when(transactionQueryService.list(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(transaction("txn-1", "FLAGGED")), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/transactions")
                        .param("accountNumber", "ACC-1")
                        .param("ruleType", "VELOCITY")
                        .param("status", "FLAGGED")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-1"))
                .andExpect(jsonPath("$.content[0].status").value("FLAGGED"));

        verify(transactionQueryService).list("ACC-1", "VELOCITY", "FLAGGED", PageRequest.of(1, 5));
    }

    @Test
    void shouldUseTheFirstPage_whenNoPagingParametersAreGiven() throws Exception {
        when(transactionQueryService.list(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/transactions")).andExpect(status().isOk());

        verify(transactionQueryService).list(null, null, null, PageRequest.of(0, 20));
    }

    // ========== get() Tests ==========

    @Test
    void shouldReturnTheTransaction_whenItExists() throws Exception {
        when(transactionQueryService.get("txn-1")).thenReturn(TransactionDetailResponse.builder()
                .transaction(transaction("txn-1", "CLEAR"))
                .ruleHits(List.of())
                .build());

        mockMvc.perform(get("/transactions/txn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.transaction.status").value("CLEAR"));
    }

    // proves the advice is wired to the controller; the mappings themselves live in GlobalExceptionHandlerTest
    @Test
    void shouldReturnNotFound_whenTheTransactionIsUnknown() throws Exception {
        when(transactionQueryService.get("missing"))
                .thenThrow(new TransactionNotFoundException(FraudEngineErrorMessages.TRANSACTION_NOT_FOUND));

        mockMvc.perform(get("/transactions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("SERVICE_ERROR"))
                .andExpect(jsonPath("$.errors[0].code").value("001"));
    }

    // ========== Helper Methods ==========

    private static EvaluatedTransactionResponse transaction(final String transactionId, final String status) {
        return EvaluatedTransactionResponse.builder()
                .transactionId(transactionId)
                .status(status)
                .build();
    }
}
