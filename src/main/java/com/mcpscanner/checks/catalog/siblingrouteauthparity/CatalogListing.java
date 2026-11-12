package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.checks.shared.probe.McpMethodClass;

final class CatalogListing {

    private CatalogListing() {}

    static boolean is(HttpRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.method())) {
            return false;
        }
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(request);
        return envelope.isJsonRpc2() && envelope.method()
                .map(method -> McpMethodClass.of(method, envelope.notification()) == McpMethodClass.CATALOG)
                .orElse(false);
    }

    static boolean pagesOn(HttpRequest listing) {
        return JsonRpcEnvelope.of(listing).root().map(root -> root.path("params").has("cursor")).orElse(false);
    }

    static HttpRequest firstPage(HttpRequest listing) {
        return JsonRpcEnvelope.of(listing).root()
                .filter(root -> root.path("params").has("cursor"))
                .map(CatalogListing::withoutCursor)
                .map(root -> listing.withBody(root.toString()))
                .orElse(listing);
    }

    private static JsonNode withoutCursor(JsonNode root) {
        ObjectNode copy = root.deepCopy();
        ((ObjectNode) copy.get("params")).remove("cursor");
        return copy;
    }
}
