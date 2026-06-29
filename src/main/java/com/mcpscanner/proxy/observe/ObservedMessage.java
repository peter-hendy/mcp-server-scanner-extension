package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;

public record ObservedMessage(Direction direction,
                              TransportType transport,
                              String sessionId,
                              String jsonrpcId,
                              String method,
                              HttpRequest request,
                              JsonNode parsed,
                              Integer status) {
}
