package com.fraudengine.core.controller;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Tag(name = "Transactions", description = "API to fetch Transactions and associated Fraud evaluations")
@SecurityRequirement(name = "bearerAuth")
@Validated
public interface TransactionInterface {

    @Operation(
            summary = "List evaluated transactions",
            description = "Paginated and filterable list of evaluated transactions")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successful operation"),
                @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    ResponseEntity<Page<EvaluatedTransactionResponse>> list(
            @Parameter(description = "Filter by account number") String accountNumber,
            @Parameter(description = "Filter by rule type") String ruleType,
            @Parameter(description = "Filter by status: FLAGGED or CLEAR") String status,
            @Parameter(description = "Zero-based page index") int page,
            @Parameter(description = "Page size") int size);

    @Operation(
            summary = "Get a single transaction",
            description = "fetches full details for single transaction")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successful operation"),
                @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
                @ApiResponse(responseCode = "404", description = "Transaction not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    ResponseEntity<TransactionDetailResponse> get(@Parameter(description = "Transaction id") String id);
}
