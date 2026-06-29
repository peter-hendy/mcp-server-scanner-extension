package com.mcpscanner.proxy.observe.preflight;

import java.net.InetSocketAddress;

/**
 * Real automated check: Burp's own proxy listener must be reachable on loopback, because scanner
 * traffic the proxy intermediates flows through it. A failure here is the <em>Burp listener</em>
 * being down, not the upstream MCP server — the message says so explicitly to stop operators chasing
 * the wrong host.
 */
public final class ProxyListenerReachableCheck implements PreflightCheck {

    public static final String LABEL = "Burp proxy listener reachable";

    private static final String LOOPBACK = "127.0.0.1";

    private final PortProbe probe;
    private final int listenerPort;

    public ProxyListenerReachableCheck(PortProbe probe, int listenerPort) {
        this.probe = probe;
        this.listenerPort = listenerPort;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public PreflightResult run() {
        InetSocketAddress listener = new InetSocketAddress(LOOPBACK, listenerPort);
        if (probe.isReachable(listener)) {
            return PreflightResult.ok("Burp proxy listener is reachable on "
                    + LOOPBACK + ":" + listenerPort + ".");
        }
        return PreflightResult.fail("Burp's proxy listener is not reachable on "
                + LOOPBACK + ":" + listenerPort + " (this is Burp's own listener, not the upstream "
                + "target). Check Proxy -> Proxy settings -> Proxy listeners, or set the correct "
                + "port if you run the listener elsewhere.");
    }
}
