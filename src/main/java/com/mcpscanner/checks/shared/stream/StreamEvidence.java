package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class StreamEvidence {

    private StreamEvidence() {
    }

    static HttpResponse response(int status, Map<String, List<String>> headers, byte[] prefix) {
        return HttpResponse.httpResponse(raw(status, headers, prefix));
    }

    static String raw(int status, Map<String, List<String>> headers, byte[] prefix) {
        StringBuilder raw = new StringBuilder("HTTP/1.1 ").append(status).append(' ')
                .append(StatusReasons.of(status)).append("\r\n");
        headers.forEach((name, values) -> values.forEach(value ->
                raw.append(name).append(": ").append(value).append("\r\n")));
        return raw.append("\r\n").append(Utf8Prefix.wholeCharacters(prefix)).toString();
    }
}
