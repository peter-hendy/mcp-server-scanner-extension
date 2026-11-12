package com.mcpscanner.scan.field;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.JsonLeafLocator;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.List;

public final class ClientMetadataFieldGenerator {

    private static final String REDIRECT_URIS = "redirect_uris";

    public List<AuditInsertionPoint> fieldsOf(HttpRequest request) {
        byte[] body = request.body().getBytes();
        if (!"POST".equalsIgnoreCase(request.method()) || !isClientMetadata(body)) {
            return List.of();
        }
        return JsonLeafLocator.rootLeaves(body).stream()
                .<AuditInsertionPoint>map(leaf -> JsonFieldInsertionPoint.namedByPath(request, leaf))
                .toList();
    }

    private static boolean isClientMetadata(byte[] body) {
        try {
            JsonNode root = McpObjectMapper.INSTANCE.readTree(body);
            return root != null && root.isObject() && root.path(REDIRECT_URIS).isArray();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
