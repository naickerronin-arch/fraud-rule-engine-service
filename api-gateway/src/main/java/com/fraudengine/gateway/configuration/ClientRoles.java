package com.fraudengine.gateway.configuration;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class ClientRoles {

    private static final Map<String, String> ROLES = Map.of(
            "compliance-portal", "ComplianceTeam",
            "fraud-portal", "FraudTeam"
    );

    public static String forClientId(String clientId) {
        return ROLES.get(clientId);
    }
}
