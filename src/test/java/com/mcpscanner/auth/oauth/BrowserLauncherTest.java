package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserLauncherTest {

    private static final URI SUSPICIOUS_AUTHORIZE = URI.create("http://10.0.0.1/authorize");
    private static final SuspiciousDestinationGate APPROVING_GATE =
            (url, purpose) -> SuspiciousDestinationGate.Decision.allow();
    private static final SuspiciousDestinationGate DENYING_GATE =
            (url, purpose) -> SuspiciousDestinationGate.Decision.deny(null);

    @Test
    void desktopLauncherOpensADestinationTheInjectedGateApproved() {
        List<URI> browsed = new ArrayList<>();
        BrowserLauncher launcher = BrowserLauncher.validated(
                new DesktopBrowserLauncher(McpEventLog.noop(), recordingBrowser(browsed)), APPROVING_GATE);

        launcher.open(SUSPICIOUS_AUTHORIZE);

        assertThat(browsed).containsExactly(SUSPICIOUS_AUTHORIZE);
    }

    @Test
    void desktopLauncherReportsUnsupportedDesktop() {
        BrowserLauncher launcher = new DesktopBrowserLauncher(McpEventLog.noop(), unsupportedBrowser());

        assertThatThrownBy(() -> launcher.open(SUSPICIOUS_AUTHORIZE))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void validatedLauncherDelegatesApprovedUrl() {
        AtomicReference<URI> received = new AtomicReference<>();
        BrowserLauncher validated = BrowserLauncher.validated(received::set, APPROVING_GATE);

        validated.open(URI.create("https://issuer.example.com/authorize"));

        assertThat(received.get()).isEqualTo(URI.create("https://issuer.example.com/authorize"));
    }

    @Test
    void validatedLauncherBlocksDeniedUrlBeforeDelegation() {
        AtomicReference<URI> received = new AtomicReference<>();
        BrowserLauncher validated = BrowserLauncher.validated(received::set, DENYING_GATE);

        assertThatThrownBy(() -> validated.open(URI.create("javascript:alert(1)")))
                .isInstanceOf(OAuthException.class)
                .hasMessageStartingWith("Refusing to launch browser");
        assertThat(received.get()).isNull();
    }

    private static DesktopBrowserLauncher.SystemBrowser recordingBrowser(List<URI> browsed) {
        return new DesktopBrowserLauncher.SystemBrowser() {
            @Override
            public boolean supported() {
                return true;
            }

            @Override
            public void browse(URI uri) {
                browsed.add(uri);
            }
        };
    }

    private static DesktopBrowserLauncher.SystemBrowser unsupportedBrowser() {
        return new DesktopBrowserLauncher.SystemBrowser() {
            @Override
            public boolean supported() {
                return false;
            }

            @Override
            public void browse(URI uri) {
                throw new AssertionError("must not browse when unsupported");
            }
        };
    }
}
