package com.mcpscanner.cvetargets.outofband;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.nio.charset.StandardCharsets;

final class RawRequests {

    private static final String HEADER_END = "\r\n\r\n";

    private RawRequests() {}

    static HttpRequest parse(byte[] raw) {
        String text = new String(raw, StandardCharsets.ISO_8859_1);
        int headerEnd = text.indexOf(HEADER_END);
        String head = headerEnd < 0 ? text : text.substring(0, headerEnd);
        String[] lines = head.split("\r\n");
        String[] requestLine = lines[0].split(" ");
        String host = "unknown";
        for (int line = 1; line < lines.length; line++) {
            if (lines[line].regionMatches(true, 0, "Host:", 0, 5)) {
                host = lines[line].substring(5).trim();
            }
        }
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://" + host
                + (requestLine.length > 1 ? requestLine[1] : "/")).withMethod(requestLine[0]);
        for (int line = 1; line < lines.length; line++) {
            int colon = lines[line].indexOf(':');
            if (colon > 0) {
                request = request.withHeader(lines[line].substring(0, colon), lines[line].substring(colon + 1).trim());
            }
        }
        return headerEnd < 0 ? request : request.withBody(text.substring(headerEnd + HEADER_END.length()));
    }
}
