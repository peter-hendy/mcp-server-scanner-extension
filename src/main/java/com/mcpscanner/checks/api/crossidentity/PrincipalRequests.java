package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class PrincipalRequests {

    private final CredentialStripper stripper;
    private final Set<String> credentialNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public PrincipalRequests(Supplier<AuthStrategy> primaryAuth, PrincipalRoster roster) {
        this.stripper = new CredentialStripper(primaryAuth);
        roster.all().forEach(principal -> credentialNames.addAll(principal.credentialHeaders().get().keySet()));
    }

    public HttpRequest anonymous(HttpRequest request) {
        return routed(request, Principal.ANONYMOUS_ID, Map.of());
    }

    public HttpRequest as(Principal principal, HttpRequest request) {
        return routed(request, principal.id(), principal.credentialHeaders().get());
    }

    private HttpRequest routed(HttpRequest request, String routingId, Map<String, String> credentials) {
        Map<String, String> overrides = new LinkedHashMap<>(credentials);
        overrides.put(ScannerSentinels.IDENTITY_HEADER, routingId);
        return HeaderMutation.apply(stripper.apply(request), credentialNames, overrides);
    }
}
