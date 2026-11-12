package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class RawHttpRequestStub {

    private RawHttpRequestStub() {}

    public static HttpRequest post(String body, Map<String, String> headers) {
        StringBuilder head = new StringBuilder("POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:8000\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        return parse(head.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1),
                body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpRequest fromRaw(String rawRequest) {
        byte[] raw = rawRequest.getBytes(StandardCharsets.UTF_8);
        int split = rawRequest.indexOf("\r\n\r\n");
        int headLength = rawRequest.substring(0, split + 4).getBytes(StandardCharsets.UTF_8).length;
        byte[] head = new byte[headLength];
        byte[] body = new byte[raw.length - headLength];
        System.arraycopy(raw, 0, head, 0, headLength);
        System.arraycopy(raw, headLength, body, 0, body.length);
        return parse(head, body);
    }

    private static HttpRequest parse(byte[] head, byte[] body) {
        String headText = new String(head, StandardCharsets.ISO_8859_1);
        String[] lines = headText.split("\r\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon), lines[i].substring(colon + 1).trim());
            }
        }
        return stub(lines[0], headers, body);
    }

    private static HttpRequest stub(String requestLine, Map<String, String> headers, byte[] body) {
        HttpRequest request = mock(HttpRequest.class);
        byte[] head = headBytes(requestLine, headers);
        byte[] whole = concat(head, body);
        lenient().when(request.method()).thenReturn(requestLine.split(" ")[0]);
        lenient().when(request.path()).thenReturn(requestLine.split(" ")[1]);
        lenient().when(request.body()).thenAnswer(inv -> ByteArray.byteArray(body));
        lenient().when(request.bodyToString()).thenReturn(new String(body, StandardCharsets.ISO_8859_1));
        lenient().when(request.bodyOffset()).thenReturn(head.length);
        lenient().when(request.toByteArray()).thenAnswer(inv -> ByteArray.byteArray(whole));
        lenient().when(request.toString()).thenReturn(new String(whole, StandardCharsets.UTF_8));
        lenient().when(request.headers()).thenAnswer(inv -> headerList(headers));
        lenient().when(request.headerValue(anyString())).thenAnswer(inv -> valueOf(headers, inv.getArgument(0)));
        lenient().when(request.hasHeader(anyString()))
                .thenAnswer(inv -> valueOf(headers, inv.getArgument(0)) != null);
        lenient().when(request.withBody(any(ByteArray.class)))
                .thenAnswer(inv -> stub(requestLine, headers, ((ByteArray) inv.getArgument(0)).getBytes()));
        lenient().when(request.withBody(anyString())).thenAnswer(inv -> stub(requestLine, headers,
                ((String) inv.getArgument(0)).getBytes(StandardCharsets.UTF_8)));
        lenient().when(request.withUpdatedHeader(anyString(), anyString())).thenAnswer(inv -> {
            Map<String, String> updated = new LinkedHashMap<>(headers);
            String name = inv.getArgument(0);
            updated.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
            updated.put(name, inv.getArgument(1));
            return stub(requestLine, updated, body);
        });
        HttpService service = HttpService.httpService("127.0.0.1", 8000, false);
        lenient().when(request.httpService()).thenReturn(service);
        return request;
    }

    private static String valueOf(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static List<HttpHeader> headerList(Map<String, String> headers) {
        List<HttpHeader> list = new ArrayList<>();
        headers.forEach((name, value) -> {
            HttpHeader header = mock(HttpHeader.class);
            lenient().when(header.name()).thenReturn(name);
            lenient().when(header.value()).thenReturn(value);
            list.add(header);
        });
        return list;
    }

    private static byte[] headBytes(String requestLine, Map<String, String> headers) {
        StringBuilder head = new StringBuilder(requestLine).append("\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        return head.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
