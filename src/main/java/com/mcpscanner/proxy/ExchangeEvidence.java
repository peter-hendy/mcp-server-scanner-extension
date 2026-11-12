package com.mcpscanner.proxy;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.BurpMessages;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ExchangeEvidence {

    private static final Set<String> FRAMING_HEADERS = Set.of("content-length", "transfer-encoding");

    private final BurpMessages messages = new BurpMessages();

    HttpRequestResponse of(RecordedExchange exchange) {
        return HttpRequestResponse.httpRequestResponse(requestOf(exchange), responseOf(exchange));
    }

    private HttpRequest requestOf(RecordedExchange exchange) {
        return messages.toRequest(exchange.uri().toString(), exchange.method(), exchange.requestHeaders(),
                exchange.requestBody());
    }

    private static HttpResponse responseOf(RecordedExchange exchange) {
        byte[] body = exchange.responseBody();
        byte[] head = headOf(exchange.status(), exchange.responseHeaders(), body.length)
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] whole = Arrays.copyOf(head, head.length + body.length);
        System.arraycopy(body, 0, whole, head.length, body.length);
        return HttpResponse.httpResponse(ByteArray.byteArray(whole));
    }

    private static String headOf(int status, Map<String, List<String>> headers, int bodyLength) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
        headers.forEach((name, values) -> {
            if (isRelayed(name)) {
                values.forEach(value -> head.append(name).append(": ").append(value).append("\r\n"));
            }
        });
        return head.append("Content-Length: ").append(bodyLength).append("\r\n\r\n").toString();
    }

    private static boolean isRelayed(String name) {
        return !name.startsWith(":") && !FRAMING_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
