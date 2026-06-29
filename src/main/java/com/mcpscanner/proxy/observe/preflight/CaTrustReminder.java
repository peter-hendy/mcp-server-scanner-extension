package com.mcpscanner.proxy.observe.preflight;

/**
 * Honest WARN: the loopback's JDK HTTP client must trust Burp's CA, or the upstream TLS hop fails.
 * Verifying this would need a live handshake against the running configuration, so it stays a
 * reminder rather than a faked automated check.
 */
public final class CaTrustReminder extends OperatorReminder {

    public static final String LABEL = "JDK client trusts Burp's CA";

    public CaTrustReminder() {
        super(LABEL, "Ensure the JDK truststore used by the loopback client trusts Burp's CA "
                + "certificate (export it from Settings -> Network -> TLS -> Burp CA certificate and "
                + "import it into the JVM truststore). Without it the upstream TLS hop fails. "
                + "Confirming this needs a live handshake, so it is surfaced as a reminder.");
    }
}
