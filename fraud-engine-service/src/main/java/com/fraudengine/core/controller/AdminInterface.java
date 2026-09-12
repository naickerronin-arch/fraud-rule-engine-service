package com.fraudengine.core.controller;

import com.fraudengine.core.controller.model.BadLocationResponse;
import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Tag(name = "Admin API", description = "API to allow compliance team to amend fraud evaluations")
@SecurityRequirement(name = "bearerAuth")
@Validated
public interface AdminInterface {

    @Operation(
            summary = "Override fraud verdict",
            description = "ComplianceTeam only")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successful operation"),
                @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
                @ApiResponse(responseCode = "403", description = "Caller does not have the ComplianceTeam role"),
                @ApiResponse(responseCode = "404", description = "Transaction not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    ResponseEntity<TransactionOverrideResponse> overrideTransaction(
            @Parameter(description = "Transaction id") String id, @Valid OverrideTransactionRequest request);

    @Operation(summary = "List bad locations", description = "Paginated list of bad locations")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successful operation"),
                @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    ResponseEntity<Page<BadLocationResponse>> listBadLocations(
            @Parameter(description = "Page index") int page, @Parameter(description = "Page size") int size);
}
