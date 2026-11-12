package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

final class ProbeDelivery implements UnaryOperator<HttpRequest> {

    private final CredentialHeaders credentials;
    private final Map<String, String> sentinel;

    private ProbeDelivery(CredentialHeaders credentials, Map<String, String> sentinel) {
        this.credentials = credentials;
        this.sentinel = sentinel;
    }

    static ProbeDelivery direct(CredentialHeaders credentials) {
        return new ProbeDelivery(credentials, ScannerSentinels.directDeliveryOnly());
    }

    static ProbeDelivery ownStream(CredentialHeaders credentials) {
        return new ProbeDelivery(credentials, ScannerSentinels.stripAuthOnly());
    }

    @Override
    public HttpRequest apply(HttpRequest request) {
        return HeaderMutation.apply(credentials.withMissingAdded(request), List.of(), sentinel);
    }
}
