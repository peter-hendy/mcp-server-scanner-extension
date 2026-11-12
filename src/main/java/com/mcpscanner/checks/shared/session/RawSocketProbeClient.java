package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.wire.SocketHttpTransport;
import com.mcpscanner.http.wire.UnsentRequestException;
import com.mcpscanner.http.wire.WireExchange;
import com.mcpscanner.http.wire.WireHeader;
import com.mcpscanner.http.wire.WireRequest;
import com.mcpscanner.http.wire.WireResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RawSocketProbeClient implements OutsideBurpClient {

    private final SocketHttpTransport transport;
    private final ProbeReplyReader replies;

    RawSocketProbeClient(Duration responseTimeout) {
        this(new SocketHttpTransport(responseTimeout), new ProbeReplyReader(responseTimeout));
    }

    RawSocketProbeClient(SocketHttpTransport transport, ProbeReplyReader replies) {
        this.transport = transport;
        this.replies = replies;
    }

    @Override
    public Optional<HttpRequestResponse> send(HttpRequest request) {
        try {
            WireRequest wire = WireRequest.of(request);
            return Optional.of(HttpRequestResponse.httpRequestResponse(request, responseTo(wire, request)));
        } catch (UnsentRequestException | RuntimeException unsent) {
            return Optional.empty();
        }
    }

    private HttpResponse responseTo(WireRequest wire, HttpRequest request) throws UnsentRequestException {
        try (WireExchange exchange = transport.exchange(wire)) {
            WireResponse response = exchange.response();
            return replies.read(response.status(), grouped(response.headers()), response.body(), request);
        } catch (UnsentRequestException unsent) {
            throw unsent;
        } catch (IOException | RuntimeException unanswered) {
            return null;
        }
    }

    private static Map<String, List<String>> grouped(List<WireHeader> headers) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        headers.forEach(header -> grouped.computeIfAbsent(header.name(), name -> new ArrayList<>())
                .add(header.value()));
        return grouped;
    }
}
