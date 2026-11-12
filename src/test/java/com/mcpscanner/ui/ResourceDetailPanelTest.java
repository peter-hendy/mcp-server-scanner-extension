package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.ui.widgets.DetailLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcpscanner.ui.OverviewProbe.json;
import static com.mcpscanner.ui.OverviewProbe.sectionTitles;
import static com.mcpscanner.ui.OverviewProbe.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceDetailPanelTest {

    private static final String FULL_2025_11_25 = """
            {"uri":"file:///srv/app/config/settings.yaml","name":"app_config","title":"Application config",
             "description":"Runtime settings for the application.","mimeType":"application/yaml","size":12704,
             "annotations":{"audience":["user","assistant"],"priority":0.8,"lastModified":"2026-11-09T14:22:05Z"},
             "icons":[{"src":"%s","mimeType":"image/png","sizes":["48x48"],"theme":"light"},
                      {"src":"https://cdn.example.com/icon.svg","mimeType":"image/svg+xml","theme":"dark"}],
             "_meta":{"com.example/owner":"platform-team","com.example/tags":["config","prod"]}}
            """.formatted(OverviewProbe.pngDataUri());

    private final ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aBare20241105ResourceShowsOnlyItsUri() {
        detail.show(resource("{\"uri\":\"docs://readme\",\"name\":\"readme\"}"));

        assertThat(detail.tabs().titles()).containsExactly("Overview", "Raw");
        assertThat(sectionTitles(detail.overview())).containsExactly("RESOURCE");
        assertThat(texts(detail.overview())).containsExactly("RESOURCE", "URI", "docs://readme", "scheme: docs");
    }

    @Test
    void theHeaderLineShowsTheUriInMonospace() {
        detail.show(resource("{\"uri\":\"docs://readme\",\"name\":\"readme\"}"));

        assertThat(detail.extraHeaderLine().isVisible()).isTrue();
        assertThat(detail.extraHeaderLine().getComponents()).singleElement()
                .isInstanceOfSatisfying(DetailLine.class, line -> {
                    assertThat(line.getText()).isEqualTo("docs://readme");
                    assertThat(line.getFont().getFamily()).isEqualTo("Monospaced");
                });
    }

    @Test
    void aResourceWithoutAUriHasNoHeaderLine() {
        detail.show(new McpResourceDefinition("", "Bare", null, null));

        assertThat(detail.extraHeaderLine().isVisible()).isFalse();
        assertThat(sectionTitles(detail.overview())).isEmpty();
    }

    @Test
    void aFull20251125ResourceShowsEverySection() {
        detail.show(resource(FULL_2025_11_25));

        assertThat(detail.headingLabel().getText()).isEqualTo("Application config");
        assertThat(sectionTitles(detail.overview()))
                .containsExactly("RESOURCE", "ANNOTATIONS", "ICONS", "METADATA (_META)");
        assertThat(texts(detail.overview())).contains(
                "Runtime settings for the application.",
                "file:///srv/app/config/settings.yaml", "scheme: file",
                "MIME type", "application/yaml",
                "Size", "12.4 KB (12,704 bytes)",
                "Audience", "user", "assistant",
                "Priority", "0.8 of 1.0",
                "Last modified", "2026-11-09 14:22:05 UTC",
                "https://cdn.example.com/icon.svg",
                "com.example/owner", "\"platform-team\"", "[\"config\", \"prod\"]");
        assertThat(OverviewProbe.showsAnImage(detail.overview())).isTrue();
    }

    @Test
    void aSizeThatIsNotAByteCountIsShownAsGiven() {
        detail.show(resource("{\"uri\":\"docs://a\",\"name\":\"a\",\"size\":\"huge\"}"));

        assertThat(texts(detail.overview())).contains("Size", "\"huge\"");
    }

    @Test
    void anUnparseableLastModifiedIsShownAsGiven() {
        detail.show(resource("{\"uri\":\"docs://a\",\"name\":\"a\",\"annotations\":{\"lastModified\":\"last week\"}}"));

        assertThat(texts(detail.overview())).contains("Last modified", "last week");
    }

    @Test
    void htmlLookingServerValuesAreRenderedLiterally() {
        detail.show(resource("""
                {"uri":"<html><img src=x>","name":"<html>n","description":"<html>d","mimeType":"<html>m",
                 "annotations":{"audience":["<html>a"],"lastModified":"<html>t"},
                 "_meta":{"<html>k":"<html>v"}}
                """));

        assertThat(texts(detail.overview())).contains("<html><img src=x>", "<html>a", "<html>t", "<html>k");
        assertThat(OverviewProbe.labelsWithHtmlEnabled(detail.overview())).isEmpty();
        assertThat(OverviewProbe.labelsWithHtmlEnabled(detail.extraHeaderLine())).isEmpty();
    }

    private static McpResourceDefinition resource(String raw) {
        JsonNode node = json(raw);
        return new McpResourceDefinition(node.path("uri").asText(""), node.path("name").asText(),
                node.path("description").asText(null), node.path("mimeType").asText(null), List.of(), node);
    }
}
