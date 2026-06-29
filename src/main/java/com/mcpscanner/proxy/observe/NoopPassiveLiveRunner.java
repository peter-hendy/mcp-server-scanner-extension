package com.mcpscanner.proxy.observe;

/**
 * Passive live runner that does nothing. The default until the real runner (which runs the content
 * rules over a correlated exchange's response) is wired in, so the handler can depend on the seam.
 */
public final class NoopPassiveLiveRunner implements PassiveLiveRunner {

    @Override
    public void scan(McpExchange exchange) {
    }
}
