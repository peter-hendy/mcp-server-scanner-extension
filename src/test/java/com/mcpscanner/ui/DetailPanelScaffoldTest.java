package com.mcpscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.scan.ScanTarget;
import com.mcpscanner.ui.detail.DetailSession;
import com.mcpscanner.ui.detail.DetailTabs;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DetailPanelScaffoldTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aTitleBecomesTheHeadingWithTheNameBesideIt() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();

        detail.show(resource("{\"name\":\"app_config\",\"title\":\"Application config\"}"));

        assertThat(detail.headingLabel().getText()).isEqualTo("Application config");
        assertThat(detail.nameBesideTitle().getText()).isEqualTo("app_config");
        assertThat(detail.nameBesideTitle().isVisible()).isTrue();
    }

    @Test
    void withoutATitleTheNameIsTheHeadingAndIsNotRepeated() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();
        detail.show(resource("{\"name\":\"app_config\",\"title\":\"Application config\"}"));

        detail.show(resource("{\"name\":\"app_config\",\"title\":\"  \"}"));

        assertThat(detail.headingLabel().getText()).isEqualTo("app_config");
        assertThat(detail.nameBesideTitle().isVisible()).isFalse();
    }

    @Test
    void aNonTextTitleIsIgnored() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();

        detail.show(resource("{\"name\":\"app_config\",\"title\":42}"));

        assertThat(detail.headingLabel().getText()).isEqualTo("app_config");
    }

    @Test
    void aToolFallsBackToItsAnnotationsTitle() {
        ToolDetailPanel detail = new ToolDetailPanel();

        detail.show(tool("{\"name\":\"get_record\",\"annotations\":{\"title\":\"Get record\"}}"));

        assertThat(detail.headingLabel().getText()).isEqualTo("Get record");
        assertThat(detail.nameBesideTitle().getText()).isEqualTo("get_record");
    }

    @Test
    void aToolPrefersItsOwnTitleOverTheAnnotationsTitle() {
        ToolDetailPanel detail = new ToolDetailPanel();

        detail.show(tool("{\"name\":\"get_record\",\"title\":\"Fetch\",\"annotations\":{\"title\":\"Get\"}}"));

        assertThat(detail.headingLabel().getText()).isEqualTo("Fetch");
    }

    @Test
    void headingAndNameDisableHtml() {
        ToolDetailPanel detail = new ToolDetailPanel();

        assertThat(detail.headingLabel().getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(detail.nameBesideTitle().getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void eachPanelShowsItsNounChip() {
        assertThat(new ToolDetailPanel().nounChip().getText()).isEqualTo("tool");
        assertThat(new ResourceTablePanel().detailPanel().nounChip().getText()).isEqualTo("resource");
        assertThat(new ResourceTemplateTablePanel().detailPanel().nounChip().getText()).isEqualTo("template");
        assertThat(new PromptTablePanel.PromptDetailPanel().nounChip().getText()).isEqualTo("prompt");
    }

    @Test
    void rawIsTheLastTabAndShowsTheItemAsReceived() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();

        detail.show(resource("{\"uri\":\"docs://a\",\"name\":\"a\",\"x-extra\":true}"));

        assertThat(detail.tabs().titles()).containsExactly("Overview", "Raw");
        assertThat(detail.rawView().displayedText()).contains("\"x-extra\"");
    }

    @Test
    void everyPanelHasARawTabLast() {
        ToolDetailPanel tool = new ToolDetailPanel();
        tool.show(new McpToolDefinition("t", null, null));
        ResourceTemplateDetailPanel template = new ResourceTemplateTablePanel().detailPanel();
        template.show(new McpResourceTemplateDefinition("file:///{p}", "f", null, null));
        PromptTablePanel.PromptDetailPanel prompt = new PromptTablePanel.PromptDetailPanel();
        prompt.show(new McpPromptDefinition("p", "Does", List.of()));

        assertThat(tool.tabs().titles()).containsExactly("Raw");
        assertThat(template.tabs().titles()).containsExactly("Overview", "Raw");
        assertThat(prompt.tabs().titles()).containsExactly("Description", "Arguments (0)", "Raw");
    }

    @Test
    void eachPanelExplainsWhereItsRawJsonCameFrom() {
        assertThat(rawHint(new ToolDetailPanel()))
                .isEqualTo("The whole tools/list entry as the server sent it. Key order is kept.");
        assertThat(rawHint(new ResourceTablePanel().detailPanel())).contains("resources/list");
        assertThat(rawHint(new ResourceTemplateTablePanel().detailPanel())).contains("resources/templates/list");
        assertThat(rawHint(new PromptTablePanel.PromptDetailPanel())).contains("prompts/list");
    }

    @Test
    void theExtraHeaderLineIsHiddenWhenTheSubclassSuppliesNothing() {
        RecordingPanel detail = new RecordingPanel();

        detail.show("plain");

        assertThat(detail.extraHeaderLine().isVisible()).isFalse();
    }

    @Test
    void theExtraHeaderLineShowsWhatTheSubclassSupplies() {
        RecordingPanel detail = new RecordingPanel();

        detail.show("with-line");

        assertThat(detail.extraHeaderLine().isVisible()).isTrue();
        assertThat(detail.extraHeaderLine().getComponents()).hasSize(1);
    }

    @Test
    void aNewSessionReRendersTheShownItem() {
        RecordingPanel detail = new RecordingPanel();
        detail.show("plain");
        DetailSession session = new DetailSession("2025-11-25", Map.of("completions", Map.of()));

        detail.useSession(session);

        assertThat(detail.rendered).containsExactly("plain@", "plain@2025-11-25");
        assertThat(detail.session()).isEqualTo(session);
    }

    @Test
    void anUnchangedSessionDoesNotReRender() {
        RecordingPanel detail = new RecordingPanel();
        detail.show("plain");

        detail.useSession(DetailSession.none());

        assertThat(detail.rendered).containsExactly("plain@");
    }

    @Test
    void aSessionArrivingWithNothingShownRendersNothing() {
        RecordingPanel detail = new RecordingPanel();
        detail.show("plain");
        detail.clear();

        detail.useSession(new DetailSession("2025-11-25", Map.of()));

        assertThat(detail.rendered).containsExactly("plain@");
    }

    @Test
    void aMalformedItemShowsOneWarningLineListingItsProblems() {
        ToolDetailPanel detail = new ToolDetailPanel();

        detail.show(new McpToolDefinition(null, null, null, List.of(), null, parse("{\"description\":\"x\"}")));

        assertThat(detail.problemsLine().isVisible()).isTrue();
        assertThat(detail.problemsLine().getText())
                .isEqualTo("Malformed: Missing required field \"name\". It cannot be scanned.");
        assertThat(detail.headingLabel().getText()).isEqualTo(MalformedRows.MARK);
        assertThat(detail.rawView().displayedText()).contains("\"description\"");
    }

    @Test
    void aWellFormedItemHidesTheWarningLine() {
        ToolDetailPanel detail = new ToolDetailPanel();
        detail.show(new McpToolDefinition(null, null, null));

        detail.show(new McpToolDefinition("t", null, null));

        assertThat(detail.problemsLine().isVisible()).isFalse();
    }

    @Test
    void aMalformedItemCannotBeScannedFromItsDetail() {
        ToolDetailPanel detail = new ToolDetailPanel();
        List<ScanTarget> scanned = new ArrayList<>();
        detail.bindScan(new ScanItemBinding<>(new InventoryScanner() {
            @Override
            public Optional<String> blocker(ScanTarget target) {
                return Optional.empty();
            }

            @Override
            public void scan(ScanTarget target) {
                scanned.add(target);
            }
        }, ScanTarget.Tool::new), () -> ScanMode.MCP_ONLY);

        detail.show(new McpToolDefinition(null, null, null));
        detail.header().scanButton().doClick();

        assertThat(scanned).isEmpty();
        assertThat(detail.header().scanButton().getToolTipText()).contains("malformed");
    }

    private static String rawHint(AbstractDetailPanel<?> detail) {
        return detail.rawView().hint();
    }

    private static McpResourceDefinition resource(String raw) {
        JsonNode node = parse(raw);
        return new McpResourceDefinition(node.path("uri").asText(""), node.path("name").asText(), null, null,
                List.of(), node);
    }

    private static McpToolDefinition tool(String raw) {
        JsonNode node = parse(raw);
        return new McpToolDefinition(node.path("name").asText(), null, null, List.of(), null, node);
    }

    private static JsonNode parse(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static final class RecordingPanel extends AbstractDetailPanel<String> {

        private final List<String> rendered = new ArrayList<>();

        RecordingPanel() {
            super(new DetailSubject<>("thing", name -> name, name -> "", name -> parse("{}"), "things/list"));
            installDetailCard();
        }

        @Override
        protected void showTabs(String item, DetailTabs tabs) {
            rendered.add(item + "@" + session().protocolVersion());
        }

        @Override
        protected List<JComponent> headerLine(String item) {
            return item.equals("with-line") ? List.of(new JLabel("line")) : List.of();
        }
    }
}
