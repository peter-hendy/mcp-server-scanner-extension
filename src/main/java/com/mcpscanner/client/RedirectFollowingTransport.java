package com.mcpscanner.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public final class RedirectFollowingTransport implements ProtocolHttpTransport {

    private final ProtocolHttpTransport delegate;
    private final RedirectPolicy policy;

    public RedirectFollowingTransport(ProtocolHttpTransport delegate, RedirectPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    @Override
    public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        ProtocolHttpRequest current = request;
        ProtocolHttpResponse response = delegate.send(current, timeout);
        for (int hop = 0; hop < RedirectPolicy.MAX_REDIRECTS; hop++) {
            Optional<RedirectHop> next = nextHop(current, response);
            if (next.isEmpty()) break;
            closeQuietly(response);
            current = current.redirectedTo(next.get());
            response = delegate.send(current, timeout);
        }
        policy.warnIfCrossOriginFollowed(request.uri(), response.uri());
        return response;
    }

    private static void closeQuietly(ProtocolHttpResponse abandoned) {
        try {
            abandoned.close();
        } catch (IOException ignored) {
        }
    }

    private Optional<RedirectHop> nextHop(ProtocolHttpRequest request, ProtocolHttpResponse response) {
        return policy.nextHop(response.status(), request.method(), request.uri(), response.firstHeader("Location"));
    }
}
