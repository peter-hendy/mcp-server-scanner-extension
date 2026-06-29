package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;

import java.time.Instant;

public record McpExchange(String sessionId,
                          TransportType transport,
                          Direction direction,
                          String jsonrpcId,
                          int generation,
                          String method,
                          HttpRequest request,
                          JsonNode decodedResponse,
                          Integer status,
                          Instant timing,
                          ExposureSurface exposureSurface) {

    public LinkKey link() {
        return new LinkKey(sessionId, jsonrpcId, generation);
    }
}
