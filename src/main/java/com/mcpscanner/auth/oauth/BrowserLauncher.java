package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;

@FunctionalInterface
public interface BrowserLauncher {

    String PURPOSE = "Browser launch (authorization endpoint)";

    void open(URI uri);

    static BrowserLauncher desktop(McpEventLog eventLog) {
        return new DesktopBrowserLauncher(eventLog, DesktopBrowserLauncher.SystemBrowser.awtDesktop());
    }

    static BrowserLauncher promptingLogin(BrowserLauncher delegate) {
        return new LoginPromptingBrowserLauncher(delegate);
    }

    static BrowserLauncher validated(BrowserLauncher delegate, SuspiciousDestinationGate gate) {
        return uri -> {
            SuspiciousDestinationGate.Decision decision = gate.evaluate(uri, FetchPurpose.of(PURPOSE, null));
            if (decision.isDenied()) {
                throw new OAuthException("Refusing to launch browser: " + decision.userMessage());
            }
            delegate.open(uri);
        };
    }
}
