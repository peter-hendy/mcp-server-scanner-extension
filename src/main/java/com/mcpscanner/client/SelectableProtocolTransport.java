package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class SelectableProtocolTransport implements ProtocolHttpTransport {

    static final String BURP_NETWORKING_TURNED_OFF = "Burp networking turned off: HTTP traffic now uses the JDK "
            + "client (no Burp client TLS certificate)";
    static final String BURP_NETWORKING_TURNED_ON = "Burp networking turned on: HTTP traffic now uses Burp's "
            + "networking, including any client TLS certificate";

    private final BooleanSupplier useBurpNetworking;
    private final ProtocolHttpTransport burpTransport;
    private final ProtocolHttpTransport jdkTransport;
    private final McpEventLog eventLog;
    private final AtomicReference<Boolean> lastUsedBurp = new AtomicReference<>();

    public SelectableProtocolTransport(BooleanSupplier useBurpNetworking, ProtocolHttpTransport burpTransport,
                                       ProtocolHttpTransport jdkTransport, McpEventLog eventLog) {
        this.useBurpNetworking = useBurpNetworking;
        this.burpTransport = burpTransport;
        this.jdkTransport = jdkTransport;
        this.eventLog = eventLog;
    }

    @Override
    public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        return selected().send(request, timeout);
    }

    private ProtocolHttpTransport selected() {
        boolean useBurp = useBurpNetworking.getAsBoolean();
        logIfChoiceChanged(lastUsedBurp.getAndSet(useBurp), useBurp);
        return useBurp ? burpTransport : jdkTransport;
    }

    private void logIfChoiceChanged(Boolean previouslyUsedBurp, boolean useBurp) {
        if (previouslyUsedBurp != null && previouslyUsedBurp != useBurp) {
            eventLog.info(useBurp ? BURP_NETWORKING_TURNED_ON : BURP_NETWORKING_TURNED_OFF);
        }
    }
}
