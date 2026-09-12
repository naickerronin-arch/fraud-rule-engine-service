package com.fraudengine.login.controller;

import com.fraudengine.login.service.dto.LoginRequest;
import com.fraudengine.login.service.dto.LoginResponse;
import com.fraudengine.login.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController implements LoginControllerInterface {

    private final LoginService loginService;
    @Override
    @PostMapping("/auth")
    public ResponseEntity<LoginResponse> login(
            @RequestBody final LoginRequest loginRequest) {
        return ResponseEntity.ok(loginService.fetchToken(loginRequest.getUsername(), loginRequest.getPassword()));
    }
}
