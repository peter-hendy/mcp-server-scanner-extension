package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class StreamedReplyFallbackTransport implements ProtocolHttpTransport {

    static final String BYPASSING_BURP_WARNING = "%s: the server streams this reply (text/event-stream), which Burp"
            + " doesn't pass to extensions, so these scan requests now bypass Burp's network settings (client TLS"
            + " certificate, upstream proxy, session handling rules) and use the JDK client until disconnect. Each"
            + " request Burp refused, including any others already in flight, reached the server and was sent again,"
            + " so it ran twice.";

    private final ProtocolHttpTransport burpTransport;
    private final ProtocolHttpTransport jdkTransport;
    private final BooleanSupplier forwarding;
    private final McpEventLog eventLog;
    private final Set<StreamedRoute> streamingRoutes = ConcurrentHashMap.newKeySet();

    public StreamedReplyFallbackTransport(ProtocolHttpTransport burpTransport, ProtocolHttpTransport jdkTransport,
                                          BooleanSupplier forwarding, McpEventLog eventLog) {
        this.burpTransport = burpTransport;
        this.jdkTransport = jdkTransport;
        this.forwarding = forwarding;
        this.eventLog = eventLog;
    }

    @Override
    public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        if (!forwarding.getAsBoolean()) {
            return burpTransport.send(request, timeout);
        }
        StreamedRoute route = StreamedRoute.of(request);
        if (streamingRoutes.contains(route)) {
            return jdkTransport.send(request, timeout);
        }
        try {
            return burpTransport.send(request, timeout);
        } catch (BurpStreamingResponseException streamed) {
            return resentOutsideBurp(route, request, timeout);
        }
    }

    public void forgetStreamingRoutes() {
        streamingRoutes.clear();
    }

    private ProtocolHttpResponse resentOutsideBurp(StreamedRoute route, ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        boolean firstRefusal = streamingRoutes.add(route);
        try {
            ProtocolHttpResponse response = jdkTransport.send(request, timeout);
            if (firstRefusal) {
                eventLog.warn(BYPASSING_BURP_WARNING.formatted(route));
            }
            return response;
        } catch (IOException | InterruptedException | RuntimeException failed) {
            streamingRoutes.remove(route);
            throw failed;
        }
    }
}
