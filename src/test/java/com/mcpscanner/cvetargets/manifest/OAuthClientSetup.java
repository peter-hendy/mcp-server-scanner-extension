package com.mcpscanner.cvetargets.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record OAuthClientSetup(String registration, String clientId, String clientSecret,
                               String tokenEndpointAuthMethod) {

    public static final String EXTENSION_DCR = "extension-dcr";
    public static final String HARNESS_DCR = "dcr";
    public static final String STATIC = "static";
    private static final Set<String> REGISTRATIONS = Set.of(EXTENSION_DCR, HARNESS_DCR, STATIC);

    List<String> violations() {
        List<String> violations = new ArrayList<>();
        if (!REGISTRATIONS.contains(registration)) {
            violations.add("auth.token.client.registration must be one of " + REGISTRATIONS);
        }
        if (STATIC.equals(registration) && (clientId == null || clientId.isBlank())) {
            violations.add("auth.token.client.clientId is required for static registration");
        }
        if (HARNESS_DCR.equals(registration) && (tokenEndpointAuthMethod == null || tokenEndpointAuthMethod.isBlank())) {
            violations.add("auth.token.client.tokenEndpointAuthMethod is required for dcr registration");
        }
        return violations;
    }
}
