package com.mcpscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.ChipVariant;
import com.mcpscanner.ui.detail.DetailSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDetailPrettyViewTest {

    private static final String NO_HINTS = "No behaviour hints declared. Spec defaults apply:";

    private static final String PETERS_SCHEMA = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\","
            + "\"properties\":{\"id\":{\"format\":\"uuid\",\"pattern\":\"^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8]"
            + "[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000|"
            + "ffffffff-ffff-ffff-ffff-ffffffffffff)$\",\"type\":\"string\"}},\"required\":[\"id\"],"
            + "\"type\":\"object\"}";

    private static final String TOOL_2025_06_18 = "{\"name\":\"search_issues\",\"title\":\"Search issues\","
            + "\"description\":\"Finds issues.\","
            + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
            + "\"limit\":{\"type\":\"integer\"}},\"required\":[\"query\"]},"
            + "\"outputSchema\":{\"type\":\"object\",\"properties\":{\"issues\":{\"type\":\"array\"}}},"
            + "\"annotations\":{\"destructiveHint\":false,\"idempotentHint\":true},"
            + "\"_meta\":{\"vendor/trace\":\"on\"}}";

    private final ToolDetailPanel detail = new ToolDetailPanel();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void petersSchemaShowsOneParameterWithItsDraft07DialectAndTheSpecDefaults() {
        detail.show(tool("{\"name\":\"get_record\",\"inputSchema\":" + PETERS_SCHEMA + "}"));

        assertThat(detail.tabs().titles()).containsExactly("Parameters (1)", "Raw");
        assertThat(detail.parametersTable().parameterCount()).isEqualTo(1);
        assertThat(chipTexts(detail.parametersTable())).contains("JSON Schema draft-07");
        assertThat(headerTexts()).containsExactly(NO_HINTS, "Destructive (default)", "Open world (default)");
        assertThat(detail.argumentsHint().isVisible()).isTrue();
    }

    @Test
    void theDefaultedDestructiveChipNeedsAttention() {
        detail.show(tool("{\"name\":\"get_record\"}"));

        assertThat(headerChip("Destructive (default)").variant()).isEqualTo(ChipVariant.ATTENTION);
        assertThat(headerChip("Open world (default)").variant()).isEqualTo(ChipVariant.NEUTRAL);
    }

    @Test
    void aBare2024ToolHasOnlyRawAndTheSpecDefaults() {
        detail.show(tool("{\"name\":\"ping\",\"inputSchema\":{\"type\":\"object\"}}"));

        assertThat(detail.tabs().titles()).containsExactly("Raw");
        assertThat(headerTexts()).containsExactly(NO_HINTS, "Destructive (default)", "Open world (default)");
        assertThat(detail.argumentsHint().isVisible()).isFalse();
    }

    @Test
    void aRootUnionWithoutPropertiesStillShowsItsParametersTab() {
        detail.show(tool("{\"name\":\"pick\",\"inputSchema\":{\"type\":\"object\",\"oneOf\":"
                + "[{\"required\":[\"a\"]},{\"required\":[\"b\"]}]}}"));

        assertThat(detail.tabs().titles()).containsExactly("Parameters (0)", "Raw");
        assertThat(detail.argumentsHint().isVisible()).isTrue();
    }

    @Test
    void anEmptySchemaHasNoParametersTab() {
        detail.show(tool("{\"name\":\"ping\",\"inputSchema\":{}}"));

        assertThat(detail.tabs().titles()).containsExactly("Raw");
    }

    @Test
    void aDescribed2024ToolAddsOnlyTheDescriptionTab() {
        detail.show(tool("{\"name\":\"ping\",\"description\":\"Pings.\",\"inputSchema\":{\"type\":\"object\"}}"));

        assertThat(detail.tabs().titles()).containsExactly("Description", "Raw");
    }

    @Test
    void aReadOnly2025_03ToolHidesTheDestructiveAndIdempotentHints() {
        detail.show(tool("{\"name\":\"list_files\",\"inputSchema\":{\"type\":\"object\"},\"annotations\":"
                + "{\"readOnlyHint\":true,\"destructiveHint\":true,\"idempotentHint\":true,\"openWorldHint\":false}}"));

        assertThat(headerTexts()).containsExactly("Read-only", "Closed world");
        assertThat(headerChip("Read-only").variant()).isEqualTo(ChipVariant.POSITIVE);
    }

    @Test
    void anExplicitDestructiveHintCarriesNoDefaultMarker() {
        detail.show(tool("{\"name\":\"rm\",\"annotations\":{\"destructiveHint\":true,\"openWorldHint\":true}}"));

        assertThat(headerTexts()).containsExactly("Destructive", "Open world");
        assertThat(headerChip("Destructive").variant()).isEqualTo(ChipVariant.ATTENTION);
    }

    @Test
    void aWritable2025_06ToolShowsItsHintsOutputSchemaAndMetadata() {
        detail.useSession(new DetailSession("2025-06-18", Map.of()));

        detail.show(tool(TOOL_2025_06_18));

        assertThat(detail.headingLabel().getText()).isEqualTo("Search issues");
        assertThat(headerTexts()).containsExactly("Non-destructive", "Idempotent", "Open world (default)");
        assertThat(headerChip("Idempotent").variant()).isEqualTo(ChipVariant.POSITIVE);
        assertThat(detail.tabs().titles())
                .containsExactly("Description", "Parameters (2)", "Output schema", "Details", "Raw");
        assertThat(chipTexts(detail.outputSchemaTable())).contains("JSON Schema unspecified");
        assertThat(labelTexts(detail.detailsView())).contains("vendor/trace");
    }

    @Test
    void aFull2025_11ToolShowsTasksIconsAndTheDefaultDialect() {
        detail.useSession(new DetailSession("2025-11-25", Map.of()));

        detail.show(tool(fullTool()));

        assertThat(headerTexts()).containsExactly("Read-only", "Closed world", "Tasks: optional");
        assertThat(detail.tabs().titles())
                .containsExactly("Description", "Parameters (1)", "Output schema", "Details", "Raw");
        assertThat(chipTexts(detail.parametersTable())).contains("JSON Schema 2020-12 (default)");
        assertThat(SwingTree.descendants(detail.detailsView())).anyMatch(
                component -> component instanceof JLabel label && label.getIcon() != null);
        assertThat(labelTexts(detail.detailsView())).contains("ui/hint");
    }

    @Test
    void anMcpAppsToolShowsItsUiResourceInDetails() {
        detail.show(tool("{\"name\":\"chart\",\"_meta\":{\"ui\":{\"resourceUri\":\"ui://charts/bar.html\"}}}"));

        assertThat(detail.tabs().titles()).contains("Details");
        assertThat(allTexts(detail.detailsView())).contains("MCP APPS", "UI resource", "ui://charts/bar.html");
    }

    @Test
    void theLegacyMcpAppsKeyIsRecognised() {
        detail.show(tool("{\"name\":\"chart\",\"_meta\":{\"ui/resourceUri\":\"ui://charts/legacy.html\"}}"));

        assertThat(allTexts(detail.detailsView())).contains("ui://charts/legacy.html");
    }

    @Test
    void theSameToolOnA2025_06SessionHasAnUnspecifiedDialect() {
        detail.show(tool(fullTool()));

        detail.useSession(new DetailSession("2025-06-18", Map.of()));

        assertThat(chipTexts(detail.parametersTable())).contains("JSON Schema unspecified");
    }

    @Test
    void theNextToolDropsTheOutputSchemaAndDetailsTabs() {
        detail.show(tool(TOOL_2025_06_18));

        detail.show(tool("{\"name\":\"ping\",\"inputSchema\":{\"type\":\"object\"}}"));

        assertThat(detail.tabs().titles()).containsExactly("Raw");
        assertThat(detail.argumentsHint().isVisible()).isFalse();
    }

    @Test
    void aToolBuiltWithoutRawFallsBackToItsAnnotationsRecord() {
        detail.show(new McpToolDefinition("list", null, null, List.of(),
                new ToolAnnotations(null, true, null, null, false)));

        assertThat(headerTexts()).containsExactly("Read-only", "Closed world");
    }

    @Test
    void aToolBuiltWithoutRawStillShowsItsParameters() {
        detail.show(new McpToolDefinition("find", null, PETERS_SCHEMA));

        assertThat(detail.tabs().titles()).containsExactly("Parameters (1)", "Raw");
    }

    @Test
    void aNonBooleanHintCountsAsAbsent() {
        detail.show(tool("{\"name\":\"odd\",\"annotations\":{\"readOnlyHint\":\"yes\"}}"));

        assertThat(headerTexts()).containsExactly(NO_HINTS, "Destructive (default)", "Open world (default)");
    }

    @Test
    void aTitleOnlyAnnotationStillCountsAsNoHints() {
        detail.show(tool("{\"name\":\"odd\",\"annotations\":{\"title\":\"Odd\"}}"));

        assertThat(headerTexts()).first().isEqualTo(NO_HINTS);
    }

    @Test
    void serverSuppliedHeaderTextIsNeverRenderedAsHtml() {
        detail.show(tool("{\"name\":\"t\",\"execution\":{\"taskSupport\":\"<html><b>x\"}}"));

        assertThat(headerLabels()).isNotEmpty()
                .allMatch(label -> Boolean.TRUE.equals(label.getClientProperty("html.disable")));
        assertThat(headerTexts()).contains("Tasks: <html><b>x");
    }

    private Chip headerChip(String text) {
        return headerLabels().stream()
                .filter(Chip.class::isInstance)
                .map(Chip.class::cast)
                .filter(chip -> chip.getText().equals(text))
                .findFirst()
                .orElseThrow();
    }

    private List<String> headerTexts() {
        return headerLabels().stream().map(JLabel::getText).toList();
    }

    private List<JLabel> headerLabels() {
        return SwingTree.descendants(detail.extraHeaderLine()).stream()
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .toList();
    }

    private static List<String> chipTexts(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(Chip.class::isInstance)
                .map(component -> ((Chip) component).getText())
                .toList();
    }

    private static List<String> labelTexts(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(JLabel.class::isInstance)
                .map(component -> ((JLabel) component).getText())
                .toList();
    }

    private static List<String> allTexts(Container root) {
        return SwingTree.descendants(root).stream()
                .map(component -> component instanceof JLabel label ? label.getText()
                        : component instanceof javax.swing.text.JTextComponent text ? text.getText() : null)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String fullTool() {
        return "{\"name\":\"search_issues\",\"title\":\"Search issues\",\"description\":\"Finds issues.\","
                + "\"icons\":[{\"src\":\"" + pngDataUri() + "\",\"mimeType\":\"image/png\"}],"
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}},"
                + "\"outputSchema\":{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"integer\"}}},"
                + "\"annotations\":{\"title\":\"Search\",\"readOnlyHint\":true,\"destructiveHint\":false,"
                + "\"idempotentHint\":true,\"openWorldHint\":false},"
                + "\"execution\":{\"taskSupport\":\"optional\"},"
                + "\"_meta\":{\"ui/hint\":\"compact\"}}";
    }

    private static String pngDataUri() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static McpToolDefinition tool(String raw) {
        JsonNode node = parse(raw);
        return new McpToolDefinition(node.path("name").asText(), node.path("description").asText(null),
                node.has("inputSchema") ? node.get("inputSchema").toString() : null, List.of(), null, node);
    }

    private static JsonNode parse(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
