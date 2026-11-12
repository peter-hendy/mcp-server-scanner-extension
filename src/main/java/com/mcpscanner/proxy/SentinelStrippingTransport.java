package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpTransport;

import java.io.IOException;
import java.time.Duration;

public final class SentinelStrippingTransport implements ProtocolHttpTransport {

    private final ProtocolHttpTransport delegate;

    public SentinelStrippingTransport(ProtocolHttpTransport delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        return delegate.send(withoutSentinels(request), timeout);
    }

    private static ProtocolHttpRequest withoutSentinels(ProtocolHttpRequest request) {
        return new ProtocolHttpRequest(request.uri(), request.method(),
                SentinelHeaders.withoutSentinels(request.headers()), request.body());
    }
}
