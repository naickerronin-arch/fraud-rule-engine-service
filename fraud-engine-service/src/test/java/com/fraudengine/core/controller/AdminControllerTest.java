package com.fraudengine.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fraudengine.core.controller.model.BadLocationResponse;
import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.exception.GlobalExceptionHandler;
import com.fraudengine.core.service.AdminService;
import com.fraudengine.core.util.MessageUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private static final String OVERRIDE_PATH = "/admin/transactions/txn-1/override";

    @Mock
    private AdminService adminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService))
                .setControllerAdvice(new GlobalExceptionHandler(new MessageUtil(messageSource)))
                .build();
    }

    // ========== overrideTransaction() Tests ==========

    @Test
    void shouldReturnTheOverride_whenTheRequestCarriesAVerdict() throws Exception {
        when(adminService.overrideTransaction(eq("txn-1"), any())).thenReturn(TransactionOverrideResponse.builder()
                .transactionId("txn-1")
                .overriddenFlagged(false)
                .build());

        override("{\"flagged\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.overriddenFlagged").value(false));

        assertThat(submittedRequest().getFlagged()).isFalse();
    }

    // the verdict is a boxed Boolean so that a missing field fails validation instead of defaulting to false
    @Test
    void shouldReturnBadRequest_whenTheVerdictIsMissing() throws Exception {
        override("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("FIELD_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("flagged"));

        verifyNoInteractions(adminService);
    }

    // ========== listBadLocations() Tests ==========

    @Test
    void shouldSortByNewestFirst_whenListingBadLocations() throws Exception {
        when(adminService.listBadLocations(any()))
                .thenReturn(new PageImpl<>(List.of(location("JHB-001", 2)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/admin/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areaCode").value("JHB-001"))
                .andExpect(jsonPath("$.content[0].level").value(2));

        verify(adminService).listBadLocations(PageRequest.of(0, 20, Sort.by("createdAt").descending()));
    }

    // ========== Helper Methods ==========

    private ResultActions override(final String body) throws Exception {
        return mockMvc.perform(post(OVERRIDE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private OverrideTransactionRequest submittedRequest() {
        ArgumentCaptor<OverrideTransactionRequest> captor = ArgumentCaptor.forClass(OverrideTransactionRequest.class);
        verify(adminService).overrideTransaction(eq("txn-1"), captor.capture());
        return captor.getValue();
    }

    private static BadLocationResponse location(final String areaCode, final int level) {
        return BadLocationResponse.builder().areaCode(areaCode).level(level).build();
    }
}
