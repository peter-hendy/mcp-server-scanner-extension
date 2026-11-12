package com.mcpscanner.http.wire;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record WireRequest(String method, String host, int port, boolean secure, byte[] bytes) {

    public static final Set<String> FRAMING_HEADERS = Set.of("content-length", "transfer-encoding");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    public static WireRequest of(HttpRequest request) {
        HttpService service = request.httpService();
        byte[] body = bodyOf(request);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.writeBytes(head(request, body.length).getBytes(StandardCharsets.ISO_8859_1));
        wire.writeBytes(body);
        return new WireRequest(request.method(), service.host(), service.port(), service.secure(), wire.toByteArray());
    }

    private static String head(HttpRequest request, int bodyLength) {
        List<HttpHeader> headers = request.headers();
        StringBuilder head = new StringBuilder(request.method()).append(' ').append(target(request)).append(" HTTP/1.1\r\n");
        if (lacks(headers, "host")) {
            appendHeader(head, "Host", URI.create(request.url()).getRawAuthority());
        }
        headers.stream()
                .filter(header -> !FRAMING_HEADERS.contains(header.name().toLowerCase(Locale.ROOT)))
                .forEach(header -> appendHeader(head, header.name(), header.value()));
        if (lacks(headers, "connection")) {
            appendHeader(head, "Connection", "close");
        }
        if (bodyLength > 0 || BODY_METHODS.contains(request.method().toUpperCase(Locale.ROOT))) {
            appendHeader(head, "Content-Length", String.valueOf(bodyLength));
        }
        return head.append("\r\n").toString();
    }

    private static String target(HttpRequest request) {
        String path = request.path();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private static boolean lacks(List<HttpHeader> headers, String name) {
        return headers.stream().noneMatch(header -> header.name().equalsIgnoreCase(name));
    }

    private static void appendHeader(StringBuilder head, String name, String value) {
        head.append(name).append(": ").append(value).append("\r\n");
    }

    private static byte[] bodyOf(HttpRequest request) {
        ByteArray body = request.body();
        return body == null ? new byte[0] : body.getBytes();
    }
}
