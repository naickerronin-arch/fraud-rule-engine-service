package com.fraudengine.login.controller;

import com.fraudengine.login.service.dto.LoginRequest;
import com.fraudengine.login.service.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Tag(name = "Login", description = "Service to demonstrate authenticated access to Fraud rule engine")
@Validated
public interface LoginControllerInterface {

    @Operation(
            summary = "Fetch Dex token",
            description = "authentication for demon clients")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Successful operation"),
                @ApiResponse(responseCode = "500", description = "Failed to communicate with dex")
            })
    ResponseEntity<LoginResponse> login(LoginRequest loginRequest);
}
