package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.McpRequestRenderer;

import java.util.Map;

public final class HarnessRequestRenderer implements McpRequestRenderer {

    private static final String HEADER_BODY_SEPARATOR = "\r\n\r\n";

    private final McpHttpRequestFactory factory;

    public HarnessRequestRenderer(McpHttpRequestFactory factory) {
        this.factory = factory;
    }

    @Override
    public HttpRequest render(String endpoint, McpMessage message, Map<String, String> sessionHeaders) {
        String raw = factory.raw(endpoint, message, sessionHeaders);
        int separator = raw.indexOf(HEADER_BODY_SEPARATOR);
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST");
        String[] headerLines = raw.substring(0, separator).split("\r\n");
        for (int line = 1; line < headerLines.length; line++) {
            int colon = headerLines[line].indexOf(':');
            String name = headerLines[line].substring(0, colon);
            if (!name.equalsIgnoreCase("Content-Length")) {
                request = request.withHeader(name, headerLines[line].substring(colon + 1).trim());
            }
        }
        return request.withBody(raw.substring(separator + HEADER_BODY_SEPARATOR.length()));
    }

}
