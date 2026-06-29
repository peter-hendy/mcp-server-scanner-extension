package com.mcpscanner.proxy.observe.preflight;

/**
 * Honest WARN: TLS pass-through bypasses Burp's interception, so the proxy would never see the MCP
 * traffic. Montoya cannot read the TLS pass-through list, so the operator must confirm it by hand.
 */
public final class TlsPassThroughReminder extends OperatorReminder {

    public static final String LABEL = "TLS pass-through not configured for target";

    public TlsPassThroughReminder() {
        super(LABEL, "Ensure TLS pass-through is NOT configured for the target host (Settings -> "
                + "Network -> TLS -> TLS pass-through). A pass-through entry makes Burp relay the "
                + "connection without intercepting it, so the proxy would never see the MCP traffic. "
                + "Montoya cannot read this list, so verify it by hand.");
    }
}
