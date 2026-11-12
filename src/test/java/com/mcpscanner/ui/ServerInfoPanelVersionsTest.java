package com.mcpscanner.ui;

import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.serverinfo.CapabilityCard;
import com.mcpscanner.ui.serverinfo.ServerIdentityView;
import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoPanelVersionsTest {

    private final ServerInfoPanel panel = new ServerInfoPanel();

    @Test
    void a2024ServerShowsNameVersionAndTheFiveOriginalCapabilities() {
        panel.populate(ServerResults.metadataOf(ServerResults.V2024_11_05));

        assertThat(identity().heading().getText()).isEqualTo("legacy-server");
        assertThat(ShownText.of(identity())).contains("v0.9.0");
        assertThat(advertisedTitles()).containsExactly("Tools", "Resources", "Prompts", "Logging", "Experimental");
        assertThat(card("Completions").lineTexts()).containsExactly("Not advertised");
        assertThat(card("Tasks").lineTexts()).containsExactly("Not advertised");
        assertThat(card("Resources").lineTexts()).containsExactly("✓ subscribe", "✗ list changed");
        assertThat(card("Experimental").lineTexts()).containsExactly("acme.bulkSearch");
        assertThat(InfoSections.titled(panel, "Instructions").isVisible()).isFalse();
        assertThat(panel.tabs().titles()).containsExactly("Overview", "Raw");
    }

    @Test
    void a2025_03ServerAdvertisesCompletionsAndInstructions() {
        panel.populate(ServerResults.metadataOf(ServerResults.V2025_03_26));

        assertThat(card("Completions").isAdvertised()).isTrue();
        assertThat(card("Completions").lineTexts()).containsExactly("✓ supported");
        assertThat(InfoSections.titled(panel, "Instructions").getText()).isEqualTo("Call complete before prompts/get.");
    }

    @Test
    void a2025_06ServerHeadsWithItsTitleAndShowsTheNameBeside() {
        panel.populate(ServerResults.metadataOf(ServerResults.V2025_06_18));

        assertThat(identity().heading().getText()).isEqualTo("Acme Issues");
        assertThat(identity().monospacedName().getText()).isEqualTo("acme-issues-mcp");
    }

    @Test
    void a2025_11ServerShowsEveryIdentityFieldTasksAndUnknownCapabilities() {
        panel.populate(ServerResults.metadataOf(ServerResults.withDataPngIcon(ServerResults.V2025_11_25)));

        assertThat(ShownText.of(identity())).contains("Acme Issues", "acme-issues-mcp", "v2.3.1",
                "Search and triage issues across Acme repositories.", "https://example.com/acme-issues",
                "https://cdn.example.com/acme.svg");
        assertThat(SwingTree.descendants(identity()).stream().filter(JLabel.class::isInstance)
                .map(JLabel.class::cast).map(JLabel::getIcon)).anySatisfy(icon ->
                assertThat(icon).isInstanceOf(ImageIcon.class));
        assertThat(card("Tasks").lineTexts()).containsExactly("✓ list", "✓ cancel", "✓ tools/call");
        assertThat(card("Other").lineTexts()).containsExactly("x-future: {\"mode\": \"fast\"}");
    }

    @Test
    void aModernDiscoverResultReadsServerInfoFromMeta() {
        panel.populate(ServerResults.metadataOf(ServerResults.MODERN_DISCOVER));

        assertThat(identity().heading().getText()).isEqualTo("modern-server");
        assertThat(card("Tools").lineTexts()).containsExactly("✓ list changed");
        assertThat(card("Other").lineTexts()).containsExactly("None");
    }

    @Test
    void serverInfoIconsKeepTheirThemeCaption() {
        panel.populate(ServerResults.metadataOf("""
                {"protocolVersion":"2025-11-25","capabilities":{},
                 "serverInfo":{"name":"s","icons":[{"src":"https://cdn.example.com/a.svg","theme":"dark",
                                                    "mimeType":"image/svg+xml"}]}}"""));

        assertThat(ShownText.of(identity())).contains("dark · image/svg+xml");
    }

    @Test
    void metaServerInfoIconsKeepTheirThemeCaption() {
        panel.populate(ServerResults.metadataOf("""
                {"supportedVersions":["2026-07-28"],"capabilities":{},
                 "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"m",
                          "icons":[{"src":"https://cdn.example.com/m.svg","theme":"light"}]}}}"""));

        assertThat(ShownText.of(identity())).contains("light");
    }

    @Test
    void htmlLookingNameAndInstructionsAreRenderedLiterally() {
        panel.populate(new ServerMetadata(Map.of("name", "<html><img src=http://x>"),
                "<html><b>bold</b>", Map.of("<html><i>k</i>", Map.of())));

        assertThat(identity().heading().getText()).isEqualTo("<html><img src=http://x>");
        assertThat(InfoSections.titled(panel, "Instructions").getText()).isEqualTo("<html><b>bold</b>");
        assertThat(ShownText.everyLabelIsHtmlDisabled(panel)).isTrue();
    }

    @Test
    void rawShowsTheWholeResultInReceivedKeyOrder() {
        panel.populate(ServerResults.metadataOf(
                "{\"zeta\":1,\"serverInfo\":{\"name\":\"s\",\"version\":\"1\"},\"alpha\":2,\"capabilities\":{}}"));

        RawJsonView raw = rawView();
        assertThat(raw.hint()).isEqualTo("The whole initialize result as the server sent it. Key order is kept.");
        String shown = raw.displayedText();
        assertThat(shown.indexOf("\"zeta\"")).isLessThan(shown.indexOf("\"serverInfo\""));
        assertThat(shown.indexOf("\"serverInfo\"")).isLessThan(shown.indexOf("\"alpha\""));
    }

    @Test
    void rawIsHiddenWhenTheResultWasNotKept() {
        panel.populate(ServerResults.metadataOf(ServerResults.V2024_11_05));

        panel.populate(new ServerMetadata(Map.of("name", "s"), "", Map.of()));

        assertThat(panel.tabs().titles()).containsExactly("Overview");
    }

    private ServerIdentityView identity() {
        return SwingTree.descendants(panel).stream().filter(ServerIdentityView.class::isInstance)
                .map(ServerIdentityView.class::cast).findFirst().orElseThrow();
    }

    private RawJsonView rawView() {
        return SwingTree.descendants(panel).stream().filter(RawJsonView.class::isInstance)
                .map(RawJsonView.class::cast).findFirst().orElseThrow();
    }

    private List<CapabilityCard> cards() {
        return SwingTree.descendants(panel).stream().filter(CapabilityCard.class::isInstance)
                .map(CapabilityCard.class::cast).toList();
    }

    private List<String> advertisedTitles() {
        return cards().stream().filter(CapabilityCard::isAdvertised).map(CapabilityCard::title).toList();
    }

    private CapabilityCard card(String title) {
        return cards().stream().filter(card -> card.title().equals(title)).findFirst().orElseThrow();
    }
}
