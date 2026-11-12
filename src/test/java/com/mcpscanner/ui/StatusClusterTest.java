package com.mcpscanner.ui;

import com.mcpscanner.ui.state.ConnectPhase;
import com.mcpscanner.ui.state.ConnectionStatus;
import com.mcpscanner.ui.widgets.ThemeColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusClusterTest {

    @Test
    void showsABusyIndicatorOnlyWhileConnecting() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.connecting("Waiting for sign-in in your browser…"));
        assertThat(cluster.busyIndicatorVisible()).isTrue();

        cluster.update(ConnectionStatus.disconnected());
        assertThat(cluster.busyIndicatorVisible()).isFalse();
    }


    @Test
    void disconnectedShowsGrayDotAndDisconnectedText() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.disconnected());

        assertThat(cluster.statusText()).isEqualTo("Disconnected");
        assertThat(cluster.dotColor()).isEqualTo(ThemeColors.muted());
    }

    @Test
    void connectingShowsAmberDot() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.connecting("Authorizing"));

        assertThat(cluster.dotColor()).isEqualTo(ThemeColors.warning());
        assertThat(cluster.statusText()).contains("Authorizing");
    }

    @Test
    void connectedShowsGreenDot() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.connected("u", "h", 5L, 12));

        assertThat(cluster.dotColor()).isEqualTo(ThemeColors.success());
    }

    @Test
    void failedShowsRedDotAndIncludesReason() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.failed(ConnectPhase.OAUTH, "bad issuer"));

        assertThat(cluster.dotColor()).isEqualTo(ThemeColors.error());
        assertThat(cluster.statusText()).contains("bad issuer");
    }

    @Test
    void labelDisablesHtmlSoServerTextIsNeverRenderedAsMarkup() {
        StatusCluster cluster = new StatusCluster();

        assertThat(cluster.labelHtmlDisabled()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void tooltipEscapesServerControlledHtml() {
        StatusCluster cluster = new StatusCluster();

        cluster.update(ConnectionStatus.failed(ConnectPhase.OAUTH, "<html><b>x &amp; y</b>"));

        assertThat(cluster.tooltipText())
                .doesNotStartWith("<html>")
                .contains("&lt;html&gt;")
                .contains("&lt;b&gt;")
                .contains("&amp;amp;");
    }
}
