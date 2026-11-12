package com.mcpscanner.auth.oauth;

import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;

final class AsEndpointHostConsistency {

    private final McpEventLog eventLog;

    AsEndpointHostConsistency(McpEventLog eventLog) {
        this.eventLog = eventLog;
    }

    void report(AuthorizationServerMetadata metadata, URI metadataUri) {
        if (metadata.getIssuer() == null || metadataUri == null) {
            return;
        }
        String issuer = metadata.getIssuer().getValue();
        String issuerHost = URI.create(issuer).getHost();
        if (issuerHost == null) {
            return;
        }
        Observation observation = new Observation(issuerHost);
        reportIfOffHost(observation, metadata.getAuthorizationEndpointURI(), "authorization_endpoint");
        reportIfOffHost(observation, metadata.getTokenEndpointURI(), "token_endpoint");
        reportIfOffHost(observation, metadata.getRegistrationEndpointURI(), "registration_endpoint");
    }

    private record Observation(String issuerHost) {}

    private void reportIfOffHost(Observation observation, URI endpoint, String endpointName) {
        String endpointHost = endpoint != null ? endpoint.getHost() : null;
        if (endpointHost == null || endpointHost.equalsIgnoreCase(observation.issuerHost())) {
            return;
        }
        eventLog.warn("AS metadata endpoint host mismatch: " + endpointName + " is on " + endpointHost
                + " but issuer is " + observation.issuerHost() + ". Proceeding permissively.");
    }
}
