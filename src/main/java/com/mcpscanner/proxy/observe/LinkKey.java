package com.mcpscanner.proxy.observe;

public record LinkKey(String sessionId, String jsonrpcId, int generation) {
}
