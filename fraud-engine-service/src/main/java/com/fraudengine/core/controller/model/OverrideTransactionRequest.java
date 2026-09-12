package com.fraudengine.core.controller.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverrideTransactionRequest {
    private boolean flagged;
    private String reason;
    @NotBlank
    private String actionedBy;
}
