package com.mcpscanner.access;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class IdentityRequests {

    private final Supplier<PrincipalRoster> roster;

    public IdentityRequests(Supplier<PrincipalRoster> roster) {
        this.roster = roster;
    }

    public HttpRequest routed(Principal principal, HttpRequest primaryRequest) {
        return Principal.PRIMARY_ID.equals(principal.id()) ? primaryRequest : as(principal, primaryRequest);
    }

    public HttpRequest as(Principal principal, HttpRequest ownerRequest) {
        Map<String, String> overrides = new LinkedHashMap<>(principal.credentialHeaders().get());
        overrides.put(ScannerSentinels.IDENTITY_HEADER, principal.id());
        return HeaderMutation.apply(ownerRequest, replacedHeaders(), overrides);
    }

    private Set<String> replacedHeaders() {
        PrincipalRoster current = roster.get();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.add(McpHeaders.SESSION_ID);
        current.all().forEach(principal -> names.addAll(principal.credentialHeaders().get().keySet()));
        return names;
    }
}
