package com.mcpscanner.testutil;

import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ScriptedBurpHttp extends SendOnlyBurpHttp implements AutoCloseable {

    private final Function<ProtocolHttpRequest, Optional<ProtocolHttpResponse>> script;
    private final CountDownLatch released = new CountDownLatch(1);

    public ScriptedBurpHttp(Function<ProtocolHttpRequest, Optional<ProtocolHttpResponse>> script) {
        this.script = script;
    }

    public static ScriptedBurpHttp fakeMcpServer() {
        return new ScriptedBurpHttp(request -> Optional.of(FakeMcpServer.respond(request)));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        HttpResponse response = script.apply(toProtocolRequest(request))
                .map(ScriptedBurpHttp::toBurpResponse)
                .orElseGet(() -> noResponseAfter(RecordingRequestOptions.recorded(requestOptions).responseTimeout()));
        return HttpRequestResponse.httpRequestResponse(request, response);
    }

    @Override
    public void close() {
        released.countDown();
    }

    private HttpResponse noResponseAfter(Optional<Duration> responseTimeout) {
        try {
            released.await(responseTimeout.orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static ProtocolHttpRequest toProtocolRequest(HttpRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (HttpHeader header : request.headers()) {
            headers.put(header.name(), header.value());
        }
        return new ProtocolHttpRequest(URI.create(request.url()), request.method(), headers, request.bodyToString());
    }

    private static HttpResponse toBurpResponse(ProtocolHttpResponse exchange) {
        try (InputStream body = exchange.body()) {
            return rawResponse(exchange.status(), exchange.headers(), body.readAllBytes());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
