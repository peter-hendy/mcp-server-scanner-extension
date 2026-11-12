package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.cvetargets.manifest.SeedRouting;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

record IdentitySeedRouting(String identityId, Supplier<Map<String, String>> credentials,
                           Set<String> primaryCredentialNames) implements SeedRouting {

    @Override
    public Map<String, String> headers(Map<String, String> sessionHeaders) {
        Set<String> replaced = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        replaced.addAll(primaryCredentialNames);
        replaced.add(McpHeaders.SESSION_ID);
        replaced.addAll(credentials.get().keySet());
        Map<String, String> headers = new LinkedHashMap<>();
        sessionHeaders.forEach((name, value) -> {
            if (!replaced.contains(name)) {
                headers.put(name, value);
            }
        });
        headers.putAll(credentials.get());
        headers.put(ScannerSentinels.IDENTITY_HEADER, identityId);
        return headers;
    }
}
