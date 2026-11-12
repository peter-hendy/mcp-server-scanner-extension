package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.function.LongSupplier;

public final class JsonRpcBaseRequests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LongSupplier ids;

    public JsonRpcBaseRequests(LongSupplier ids) {
        this.ids = ids;
    }

    public HttpRequest build(String endpoint, Map<String, String> headers, JsonRpcCall call) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint)
                .withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        return request.withBody(body(call));
    }

    private String body(JsonRpcCall call) {
        ObjectNode envelope = JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", ids.getAsLong())
                .put("method", call.method());
        if (call.params() != null) {
            envelope.set("params", call.params());
        }
        return envelope.toString();
    }
}
