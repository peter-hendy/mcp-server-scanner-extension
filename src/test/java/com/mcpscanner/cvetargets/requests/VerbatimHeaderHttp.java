package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.wire.SocketHttpTransport;
import com.mcpscanner.http.wire.WireExchange;
import com.mcpscanner.http.wire.WireHeader;
import com.mcpscanner.http.wire.WireRequest;
import com.mcpscanner.http.wire.WireResponse;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.mcp.SseResponseParser;
import com.mcpscanner.testutil.RecordingRealHttp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class VerbatimHeaderHttp extends SendOnlyHttp {

    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int SEND_ATTEMPTS = 3;
    private static final String EVENT_STREAM = "text/event-stream";

    private final SocketHttpTransport transport;

    public VerbatimHeaderHttp() {
        this(DEFAULT_READ_TIMEOUT);
    }

    public VerbatimHeaderHttp(Duration readTimeout) {
        this.transport = new SocketHttpTransport(readTimeout);
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        try {
            return HttpRequestResponse.httpRequestResponse(request, send(WireRequest.of(request)));
        } catch (IOException | RuntimeException unanswered) {
            return HttpRequestResponse.httpRequestResponse(request, null);
        }
    }

    private HttpResponse send(WireRequest wire) throws IOException {
        try (WireExchange exchange = exchangeRetryingConnectionsDroppedBeforeAReply(wire)) {
            return toBurpResponse(exchange.response());
        }
    }

    private WireExchange exchangeRetryingConnectionsDroppedBeforeAReply(WireRequest wire) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return transport.exchange(wire);
            } catch (SocketTimeoutException timeout) {
                throw timeout;
            } catch (IOException dropped) {
                if (attempt == SEND_ATTEMPTS) {
                    throw dropped;
                }
            }
        }
    }

    private static HttpResponse toBurpResponse(WireResponse response) throws IOException {
        boolean eventStream = response.headerValue("content-type")
                .map(type -> type.toLowerCase(Locale.ROOT).contains(EVENT_STREAM))
                .orElse(false);
        String content = eventStream ? firstJsonRpcEvent(response.body()) : BoundedBodyReader.readUtf8(response.body());
        return HttpResponse.httpResponse(statusAndHeaders(response, eventStream) + "\r\n" + content);
    }

    private static String firstJsonRpcEvent(InputStream body) throws IOException {
        String json = SseResponseParser.extractJsonRpcResponse(
                new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8)));
        return json == null ? "" : json;
    }

    private static String statusAndHeaders(WireResponse response, boolean eventStream) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(response.status()).append(" \r\n");
        for (WireHeader header : response.headers()) {
            String name = header.name().toLowerCase(Locale.ROOT);
            if (WireRequest.FRAMING_HEADERS.contains(name) || (eventStream && name.equals("content-type"))) {
                continue;
            }
            head.append(RecordingRealHttp.toTitleCase(header.name())).append(": ").append(header.value()).append("\r\n");
        }
        if (eventStream) {
            head.append("Content-Type: application/json\r\n");
        }
        return head.toString();
    }
}
