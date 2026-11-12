package com.mcpscanner.ui;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.ui.widgets.CopyableSecret;
import com.mcpscanner.ui.widgets.ScrollableForm;
import com.mcpscanner.ui.widgets.SecretField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenViewHeightTest {

    private static final int PANEL_HEIGHT = 900;

    @ParameterizedTest
    @ValueSource(ints = {280, 440, 700})
    void theTokenFieldShowsItsFullHeightWithinEveryPanelWidth(int width) throws Exception {
        AtomicReference<Rectangle> bounds = new AtomicReference<>();
        AtomicReference<Integer> preferredHeight = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SecretField token = layOut(width);
            bounds.set(token.getBounds());
            preferredHeight.set(token.textField().getPreferredSize().height);
        });

        assertThat(bounds.get().height).isGreaterThanOrEqualTo(preferredHeight.get());
        assertThat(bounds.get().width).isPositive().isLessThanOrEqualTo(width);
    }

    private static SecretField layOut(int width) {
        AccessTokenView tokenView = new AccessTokenView(
                new TokenRenewal(task -> { }, new SilentReporter()), () -> new Clipboard("test"));
        AuthenticationOverview panel = new AuthenticationOverview(Clock.systemUTC(), tokenView,
                new CopyableSecret("refresh token", () -> new Clipboard("test")), new ClientManagementView(() -> new Clipboard("test")));
        panel.show(AuthenticationSummary.builder("OAuth 2.1 (Authorization code)")
                .credential(new BearerTokenAuthStrategy("eyJ" + "a".repeat(900)))
                .build());
        JScrollPane host = ScrollableForm.wrap(panel);
        host.setSize(width, PANEL_HEIGHT);
        validateTree(host);
        return tokenView.row().secret();
    }

    private static void validateTree(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                validateTree(nested);
            }
        }
    }

    private static final class SilentReporter implements TokenRenewal.Reporter {
        @Override
        public void renewed() {
        }

        @Override
        public void failed(String reason) {
        }
    }
}
