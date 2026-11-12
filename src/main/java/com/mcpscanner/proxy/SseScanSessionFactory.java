package com.mcpscanner.proxy;

import java.util.Map;

@FunctionalInterface
interface SseScanSessionFactory {

    SseScanSession create(String sseUrl, Map<String, String> headers, SseSessionContext context);
}
