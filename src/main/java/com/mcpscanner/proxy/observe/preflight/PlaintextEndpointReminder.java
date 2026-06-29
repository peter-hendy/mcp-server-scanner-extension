package com.mcpscanner.proxy.observe.preflight;

/**
 * Honest WARN for Path B: the MCP client must accept a plaintext {@code http://127.0.0.1} endpoint so
 * traffic is routed through the loopback proxy. Whether a given client tolerates that is a property of
 * the client, not anything Montoya can verify.
 */
public final class PlaintextEndpointReminder extends OperatorReminder {

    public static final String LABEL = "MCP client accepts plaintext loopback endpoint";

    public PlaintextEndpointReminder() {
        super(LABEL, "For Path B, confirm the MCP client accepts a plaintext "
                + "http://127.0.0.1 endpoint (some clients require https). If it does not, the "
                + "client cannot be pointed at the loopback proxy. This depends on the client, so it "
                + "is surfaced as a reminder.");
    }
}
