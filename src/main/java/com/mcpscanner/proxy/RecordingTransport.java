package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.http.BurpMessages;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

final class RecordingTransport implements ProtocolHttpTransport {

    private final ProtocolHttpTransport delegate;
    private final UpstreamCaptures captures;

    RecordingTransport(ProtocolHttpTransport delegate, UpstreamCaptures captures) {
        this.delegate = delegate;
        this.captures = captures;
    }

    @Override
    public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        Optional<ForwardCapture> capture = captures.current();
        ProtocolHttpResponse response = delegate.send(request, timeout);
        return capture.map(active -> recorded(active, request, response)).orElse(response);
    }

    private static ProtocolHttpResponse recorded(ForwardCapture capture, ProtocolHttpRequest request,
                                                 ProtocolHttpResponse response) {
        CapturedBody body = new CapturedBody(response.body());
        capture.record(() -> new RecordedExchange(request.uri(), request.method(),
                BurpMessages.multiValued(request.headers()), request.body(), response.status(), response.headers(),
                body.captured()));
        return new ProtocolHttpResponse(response.status(), response.headers(), body, response.uri());
    }
}
