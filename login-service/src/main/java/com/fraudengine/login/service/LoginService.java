package com.fraudengine.login.service;

import com.fraudengine.login.config.ApplicationProperties;
import com.fraudengine.login.service.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final RestClient dexRestClient;
    private final ApplicationProperties applicationProperties;

    public LoginResponse fetchToken(String clientId, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", clientId + "@fraudengine.local");
        form.add("password", password);
        form.add("scope", "openid profile email");
        form.add("client_id", clientId);
        form.add("client_secret", applicationProperties.getDexConfig().getClientSecret());

        return dexRestClient.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(LoginResponse.class);
    }
}
