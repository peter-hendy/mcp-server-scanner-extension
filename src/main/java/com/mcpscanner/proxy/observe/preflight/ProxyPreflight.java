package com.mcpscanner.proxy.observe.preflight;

import burp.api.montoya.MontoyaApi;

import java.util.List;

/**
 * Runs every {@link PreflightCheck} for the proxy feature and aggregates the outcomes into a
 * {@link PreflightReport}.
 *
 * <p>Only the things Montoya can truly verify are real OK/FAIL checks: the Burp version and the proxy
 * listener's reachability. Everything else Montoya cannot read (the streaming settings, TLS
 * pass-through, CA trust, plaintext-endpoint tolerance) is an honest {@link PreflightStatus#WARN}
 * reminder carrying the exact operator action — never a faked check for something we cannot observe.
 */
public final class ProxyPreflight {

    /** Burp's default proxy listener port; operator-overridable via the constructor. */
    public static final int DEFAULT_LISTENER_PORT = 8080;

    private final List<PreflightCheck> checks;

    public ProxyPreflight(MontoyaApi api) {
        this(api, new TcpPortProbe(), DEFAULT_LISTENER_PORT);
    }

    public ProxyPreflight(MontoyaApi api, PortProbe portProbe, int listenerPort) {
        this.checks = List.of(
                new BurpVersionCheck(api),
                new ProxyListenerReachableCheck(portProbe, listenerPort),
                new StreamingSettingsReminder(),
                new TlsPassThroughReminder(),
                new CaTrustReminder(),
                new PlaintextEndpointReminder());
    }

    public PreflightReport run() {
        List<LabelledResult> results = checks.stream()
                .map(check -> new LabelledResult(check.label(), check.run()))
                .toList();
        return new PreflightReport(results);
    }
}
