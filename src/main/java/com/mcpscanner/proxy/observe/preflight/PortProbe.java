package com.mcpscanner.proxy.observe.preflight;

import java.net.InetSocketAddress;

/**
 * Tests whether a TCP socket address accepts a connection. Injected into
 * {@link ProxyListenerReachableCheck} so the listener-reachability check is unit-testable with a fake
 * (no real sockets in tests). {@link TcpPortProbe} is the production implementation.
 */
@FunctionalInterface
public interface PortProbe {

    boolean isReachable(InetSocketAddress address);
}
