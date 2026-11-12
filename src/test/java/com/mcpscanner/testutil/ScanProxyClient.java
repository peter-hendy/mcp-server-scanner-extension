package com.mcpscanner.testutil;

import com.mcpscanner.mcp.ScannerSentinels;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class ScanProxyClient {

    private ScanProxyClient() {
    }

    public static HttpResponse<String> sendScannerToolsCall(int proxyPort, String toolName)
            throws IOException, InterruptedException {
        return sendToolsCall(proxyPort, Map.of(ScannerSentinels.STRIP_AUTH_HEADER, "1"), toolName);
    }

    public static HttpResponse<String> sendAuditedToolsCall(int proxyPort, Map<String, String> auditHeaders,
                                                            String toolName)
            throws IOException, InterruptedException {
        return sendToolsCall(proxyPort, auditHeaders, toolName);
    }

    private static HttpResponse<String> sendToolsCall(int proxyPort, Map<String, String> scannerHeaders,
                                                      String toolName)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxyPort + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"" + toolName + "\",\"arguments\":{}}}"));
        scannerHeaders.forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
