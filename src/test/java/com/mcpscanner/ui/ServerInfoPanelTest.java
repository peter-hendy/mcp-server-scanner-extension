package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.config.AuthTypeNames;

import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.ChipVariant;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.serverinfo.AuthDiscoveryReport;
import com.mcpscanner.ui.serverinfo.CapabilityCards;
import com.mcpscanner.ui.serverinfo.ServerCardView;
import com.mcpscanner.ui.serverinfo.ServerIdentityView;
import com.mcpscanner.ui.serverinfo.SupportedProtocolVersions;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoPanelTest {

    private final ServerInfoPanel panel = new ServerInfoPanel();

    @Test
    void initialStateShowsPlaceholdersAndOnlyTheOverviewTab() {
        assertThat(ShownText.of(identity())).isEqualTo(ServerIdentityView.NO_IDENTITY);
        assertThat(InfoSections.titled(panel, "Capabilities").getText()).isEqualTo(CapabilityCards.NONE_ADVERTISED);
        assertThat(InfoSections.titled(panel, "Session").getText()).isEqualTo("Not connected.");
        assertThat(InfoSections.titled(panel, "Instructions").isVisible()).isFalse();
        assertThat(panel.tabs().titles()).containsExactly("Overview");
    }

    @Test
    void populateRendersTheIdentity() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", "test-server");
        info.put("version", "1.2.3");

        panel.populate(new ServerMetadata(info, "", Map.of()));

        assertThat(ShownText.of(identity())).contains("test-server", "v1.2.3");
    }

    @Test
    void populateRendersInstructionsText() {
        panel.populate(new ServerMetadata(Map.of(), "use tools/list to start", Map.of()));

        assertThat(InfoSections.titled(panel, "Instructions").isVisible()).isTrue();
        assertThat(InfoSections.titled(panel, "Instructions").getText()).isEqualTo("use tools/list to start");
    }

    @Test
    void populateRendersCapabilitiesAsCards() {
        panel.populate(new ServerMetadata(Map.of(), "", Map.of("tools", Map.of("listChanged", true))));

        assertThat(InfoSections.titled(panel, "Capabilities").getText()).contains("Tools", "✓ list changed");
    }

    @Test
    void populateWithEmptyMetadataResetsToPlaceholders() {
        panel.populate(new ServerMetadata(Map.of("name", "x"), "instructions", Map.of("tools", Map.of())));
        panel.populate(ServerMetadata.empty());

        assertThat(ShownText.of(identity())).isEqualTo(ServerIdentityView.NO_IDENTITY);
        assertThat(InfoSections.titled(panel, "Instructions").isVisible()).isFalse();
        assertThat(InfoSections.titled(panel, "Capabilities").getText()).isEqualTo(CapabilityCards.NONE_ADVERTISED);
    }

    @Test
    void connectionSummaryPersistsAcrossMetadataRefreshAndClearsOnDisconnect() {
        panel.showConnection(new ConnectionSummary(
                TransportType.AUTO, TransportType.STREAMABLE_HTTP, "2025-11-25",
                "https://mcp.example.com/mcp", AuthTypeNames.BEARER));

        panel.populate(ServerMetadata.empty());

        assertThat(InfoSections.titled(panel, "Session").getText()).contains(
                "MCP version: 2025-11-25", "Requested mode: Auto", "Connected via: HTTP 2025 (session)",
                "Endpoint: https://mcp.example.com/mcp");

        panel.clearConnection();

        assertThat(InfoSections.titled(panel, "Session").getText()).isEqualTo("Not connected.");
    }

    @Test
    void sessionShowsTheLiveSessionIdOrNone() {
        panel.showConnection(new ConnectionSummary(TransportType.AUTO, TransportType.STREAMABLE_HTTP,
                "2025-11-25", "https://mcp.example.com/mcp", AuthTypeNames.BEARER, "sess-42"));

        assertThat(InfoSections.titled(panel, "Session").getText()).contains("Session ID: sess-42");

        panel.showConnection(summaryNegotiating("2025-11-25"));

        assertThat(InfoSections.titled(panel, "Session").getText()).contains("Session ID: none");
    }

    @Test
    void sessionListsTheVersionsTheServerSaysItSupports() {
        panel.populate(ServerResults.metadataOf("""
                {"supportedVersions":["2025-11-25","2026-07-28"],"capabilities":{}}"""));
        panel.showConnection(summaryNegotiating("2026-07-28"));

        assertThat(InfoSections.titled(panel, "Session").getText())
                .contains("Supported versions: 2025-11-25, 2026-07-28");
    }

    @Test
    void supportedVersionsReadNotSentWhenTheServerOmitsThem() {
        panel.showConnection(summaryNegotiating("2025-11-25"));
        panel.populate(ServerResults.metadataOf(ServerResults.V2025_11_25));

        assertThat(InfoSections.titled(panel, "Session").getText()).contains("Supported versions: not sent");
    }

    @Test
    void theServerCardTabAppearsWhileConnectedAndGoesOnDisconnect() {
        panel.populate(ServerResults.metadataOf(ServerResults.V2025_11_25));
        panel.showConnection(summaryNegotiating("2025-11-25"));

        assertThat(panel.tabs().titles()).containsExactly("Overview", "Server card", "Raw");

        panel.clearConnection();

        assertThat(panel.tabs().titles()).containsExactly("Overview", "Raw");
    }

    @Test
    void theServerCardFetchUsesTheConnectedEndpoint() {
        List<URI> fetched = new ArrayList<>();
        ServerCardView serverCards = new ServerCardView(Runnable::run, Runnable::run);
        ServerInfoPanel connected = new ServerInfoPanel(serverCards);
        connected.useServerCards(endpoint -> {
            fetched.add(endpoint);
            throw new IllegalStateException("offline");
        });
        connected.showConnection(summaryNegotiating("2025-11-25"));

        SwingTree.button(connected, "Fetch server card").doClick();

        assertThat(fetched).containsExactly(URI.create("https://mcp.example.com/mcp"));
        assertThat(ShownText.of(serverCards)).contains("failed: offline");
    }

    @Test
    void theAuthDiscoveryTabAppearsOnlyWithOAuthEvidenceAndClearsOnDisconnect() {
        panel.showConnection(summaryNegotiating("2025-11-25"));
        panel.showAuthDiscovery(AuthDiscoveryReport.none());

        assertThat(panel.tabs().titles()).containsExactly("Overview", "Server card");

        panel.showAuthDiscovery(new AuthDiscoveryReport(Optional.empty(),
                Optional.of(SignInEvidence.none()), Optional.empty()));

        assertThat(panel.tabs().titles()).containsExactly("Overview", "Server card", "Auth discovery");

        panel.clearConnection();

        assertThat(panel.tabs().titles()).containsExactly("Overview");
    }

    @Test
    void connectionSummaryRedactsEndpointCredentialsAndQuery() {
        panel.showConnection(new ConnectionSummary(
                TransportType.SSE, TransportType.SSE, "2024-11-05",
                "https://alice:secret@mcp.example.com/messages?token=secret#fragment",
                AuthTypeNames.CUSTOM_HEADERS));

        assertThat(InfoSections.titled(panel, "Session").getText())
                .contains("https://mcp.example.com/messages")
                .doesNotContain("alice", "secret", "token=", "fragment");
    }

    @Test
    void emptyStatesUseTheMutedUiFontNeverMonospace() {
        assertThat(SwingTree.visibleDescendants(panel).stream().filter(WrappingText.class::isInstance)
                .map(WrappingText.class::cast)).isNotEmpty().allSatisfy(placeholder -> {
                    assertThat(placeholder.getFont().getFamily()).isNotEqualTo(java.awt.Font.MONOSPACED);
                    assertThat(placeholder.getClientProperty(UiStyle.STYLE_CLASS)).isNotEqualTo(UiStyle.MONOSPACED);
                    assertThat(placeholder.getForeground()).isEqualTo(ThemeColors.muted());
                });
    }

    @Test
    void theOverviewSharesOneScrollPaneInsteadOfNestedFixedBoxes() {
        long scrollPanes = SwingTree.descendants(panel).stream()
                .filter(component -> component instanceof JScrollPane)
                .filter(component -> !SwingTree.descendants(panel).stream()
                        .filter(RawJsonView.class::isInstance).map(RawJsonView.class::cast)
                        .anyMatch(raw -> javax.swing.SwingUtilities.isDescendingFrom(component, raw)))
                .count();

        assertThat(scrollPanes).isEqualTo(1);
    }

    @Test
    void theNewestSupportedVersionCarriesALatestChip() {
        panel.showConnection(summaryNegotiating(SupportedProtocolVersions.newest()));

        assertThat(sessionChips()).singleElement().satisfies(chip -> {
            assertThat(chip.getText()).isEqualTo("latest");
            assertThat(chip.variant()).isEqualTo(ChipVariant.POSITIVE);
        });
        assertThat(InfoSections.titled(panel, "Session").getText())
                .contains("MCP version: " + SupportedProtocolVersions.newest());
    }

    @Test
    void anOlderVersionHasNoChip() {
        panel.showConnection(summaryNegotiating("2024-11-05"));

        assertThat(sessionChips()).isEmpty();
    }

    private static ConnectionSummary summaryNegotiating(String version) {
        return new ConnectionSummary(TransportType.AUTO, TransportType.STREAMABLE_HTTP, version,
                "https://mcp.example.com/mcp", AuthTypeNames.BEARER);
    }

    private List<Chip> sessionChips() {
        return SwingTree.visibleDescendants(InfoSections.find(panel, "Session").orElseThrow()).stream()
                .filter(Chip.class::isInstance).map(Chip.class::cast).toList();
    }

    private ServerIdentityView identity() {
        return SwingTree.descendants(panel).stream().filter(ServerIdentityView.class::isInstance)
                .map(ServerIdentityView.class::cast).findFirst().orElseThrow();
    }
}
