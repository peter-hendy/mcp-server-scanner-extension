package com.mcpscanner.auth.oauth;

import com.mcpscanner.logging.McpEventLog;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

final class DesktopBrowserLauncher implements BrowserLauncher {

    interface SystemBrowser {
        boolean supported();

        void browse(URI uri) throws IOException;

        static SystemBrowser awtDesktop() {
            return new SystemBrowser() {
                @Override
                public boolean supported() {
                    return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
                }

                @Override
                public void browse(URI uri) throws IOException {
                    Desktop.getDesktop().browse(uri);
                }
            };
        }
    }

    private final McpEventLog eventLog;
    private final SystemBrowser browser;

    DesktopBrowserLauncher(McpEventLog eventLog, SystemBrowser browser) {
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
        this.browser = browser;
    }

    @Override
    public void open(URI uri) {
        if (!browser.supported()) {
            eventLog.warn("Browser launch unsupported in this JVM - user must open URL manually");
            throw new OAuthException("Desktop browser launch unsupported in this JVM");
        }
        try {
            browser.browse(uri);
        } catch (IOException | RuntimeException e) {
            eventLog.warn("Browser launch failed: " + e.getMessage());
            throw new OAuthException("Failed to launch browser: " + e.getMessage(), e);
        }
    }
}
