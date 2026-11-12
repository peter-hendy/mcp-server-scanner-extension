package com.mcpscanner.integration;

import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.scan.McpRepeaterRequestFactory;
import com.mcpscanner.scan.ScanConnection;
import com.mcpscanner.scan.ScanSession;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.McpRequestRenderer;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanRequestRenderer;
import com.mcpscanner.testutil.ImmutableHttpRequest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProxiedPrimaryRequests {

    private ProxiedPrimaryRequests() {
    }

    static McpRepeaterRequestFactory of(ScannerProxyFixture proxy, Map<String, String> primaryCredentials) {
        McpScannerSession session = proxy.session();
        return new McpRepeaterRequestFactory(
                ScanSession.of(session::resolvedEndpoint, session::scannerHeaders), RequestGenerator.standard(),
                new ScanRequestRenderer(parsed(new McpHttpRequestFactory(session::nextRequestId, session)),
                        new ClientRegistrationRequestFactory()),
                () -> ScanConnection.unbound(baseline -> with(proxy.proxy().auditHeaders(baseline),
                        primaryCredentials)));
    }

    private static Map<String, String> with(Map<String, String> auditHeaders, Map<String, String> credentials) {
        Map<String, String> headers = new LinkedHashMap<>(auditHeaders);
        headers.putAll(credentials);
        return headers;
    }

    private static McpRequestRenderer parsed(McpHttpRequestFactory factory) {
        return (endpoint, message, sessionHeaders) -> {
            String[] headAndBody = factory.raw(endpoint, message, sessionHeaders).split("\r\n\r\n", 2);
            Map<String, String> headers = new LinkedHashMap<>();
            Arrays.stream(headAndBody[0].split("\r\n")).skip(1).map(line -> line.split(": ", 2))
                    .filter(header -> !List.of("content-length", "host").contains(header[0].toLowerCase()))
                    .forEach(header -> headers.put(header[0], header[1]));
            return ImmutableHttpRequest.post(endpoint, headers, headAndBody[1]);
        };
    }
}
