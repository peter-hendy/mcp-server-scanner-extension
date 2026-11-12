package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.ui.detail.DetailSession;
import com.mcpscanner.ui.detail.uri.UriTemplateOverview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import java.util.List;
import java.util.Map;

import static com.mcpscanner.ui.OverviewProbe.json;
import static com.mcpscanner.ui.OverviewProbe.rows;
import static com.mcpscanner.ui.OverviewProbe.sectionTitles;
import static com.mcpscanner.ui.OverviewProbe.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceTemplateDetailPanelTest {

    private static final String MOCK_TEMPLATE = """
            {"uriTemplate":"repo://{owner}/{repo}/issues{?state,labels*}","name":"repo_issues",
             "title":"Repository issues","description":"Issues for a repository.","mimeType":"application/json",
             "annotations":{"audience":["assistant"],"priority":0.5,"lastModified":"2026-11-09T14:22:05Z"},
             "_meta":{"com.example/team":"dev"}}
            """;
    private static final String COMPLETION = "Completion";
    private static final DetailSession WITH_COMPLETIONS =
            new DetailSession("2025-11-25", Map.of("completions", Map.of()));

    private final ResourceTemplateDetailPanel detail = new ResourceTemplateTablePanel().detailPanel();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void theMockTemplateListsEachVariableWithItsPositionAndExpansion() {
        detail.show(template(MOCK_TEMPLATE));

        assertThat(detail.tabs().titles()).containsExactly("Overview", "Raw");
        assertThat(sectionTitles(detail.overview()))
                .containsExactly("URI TEMPLATE", "VARIABLES (4)", "DETAILS", "METADATA (_META)");
        assertThat(rows(OverviewProbe.table(detail.overview()))).containsExactly(
                List.of("owner", "path segment 1", "simple"),
                List.of("repo", "path segment 2", "simple"),
                List.of("state", "query", "form query (?)"),
                List.of("labels", "query", "form query (?), exploded (*)"));
        assertThat(texts(detail.overview())).contains(
                "Issues for a repository.", "repo://{owner}/{repo}/issues{?state,labels*}",
                "MIME type", "application/json", "Audience", "assistant", "Priority", "0.5 of 1.0",
                "Last modified", "2026-11-09 14:22:05 UTC");
    }

    @Test
    void theVariablesTableHasTheMockColumns() {
        detail.show(template(MOCK_TEMPLATE));

        JTable table = OverviewProbe.table(detail.overview());
        assertThat(List.of(table.getColumnName(0), table.getColumnName(1), table.getColumnName(2)))
                .containsExactly("Variable", "Position", "RFC 6570 expansion");
    }

    @Test
    void everyOperatorIsListed() {
        detail.show(template("{\"uriTemplate\":\"https://{host}/v1{/seg}{;p}{.ext}{+rest}{?q}{&page}{#frag}\","
                + "\"name\":\"ops\"}"));

        assertThat(rows(OverviewProbe.table(detail.overview()))).extracting(row -> row.get(2)).containsExactly(
                "simple", "path (/)", "path-style (;)", "label (.)", "reserved (+)", "form query (?)",
                "query continuation (&)", "fragment (#)");
    }

    @Test
    void aBare20241105TemplateShowsTheTemplateAndItsVariablesOnly() {
        detail.show(template("{\"uriTemplate\":\"file:///{path}\",\"name\":\"files\"}"));

        assertThat(sectionTitles(detail.overview())).containsExactly("URI TEMPLATE", "VARIABLES (1)");
    }

    @Test
    void aMalformedTemplateIsShownAsSentWithAWarning() {
        detail.show(template("{\"uriTemplate\":\"a{b\",\"name\":\"broken\"}"));

        assertThat(sectionTitles(detail.overview())).containsExactly("URI TEMPLATE");
        assertThat(texts(detail.overview())).contains("a{b", UriTemplateOverview.UNREADABLE_WARNING);
    }

    @Test
    void theCompletionRowIsHiddenWithoutTheCompletionsCapability() {
        detail.useSession(new DetailSession("2025-11-25", Map.of("tools", Map.of())));

        detail.show(template(MOCK_TEMPLATE));

        assertThat(texts(detail.overview())).doesNotContain(COMPLETION);
    }

    @Test
    void theCompletionRowAppearsOnceTheCompletionsCapabilityArrives() {
        detail.show(template(MOCK_TEMPLATE));

        detail.useSession(WITH_COMPLETIONS);

        assertThat(texts(detail.overview())).contains(COMPLETION, UriTemplateOverview.COMPLETION_NOTE);
    }

    @Test
    void theCompletionRowAloneMakesTheDetailsSectionVisible() {
        detail.useSession(WITH_COMPLETIONS);

        detail.show(template("{\"uriTemplate\":\"docs://{id}\",\"name\":\"docs\"}"));

        assertThat(sectionTitles(detail.overview())).containsExactly("URI TEMPLATE", "VARIABLES (1)", "DETAILS");
    }

    @Test
    void htmlLookingServerValuesAreRenderedLiterally() {
        detail.show(template("""
                {"uriTemplate":"<html><img src=x>{id}","name":"<html>n","mimeType":"<html>m",
                 "annotations":{"audience":["<html>a"]},"_meta":{"<html>k":"<html>v"}}
                """));

        JTable table = OverviewProbe.table(detail.overview());
        assertThat(texts(detail.overview())).contains("<html><img src=x>{id}", "<html>m", "<html>a", "<html>k");
        assertThat(OverviewProbe.labelsWithHtmlEnabled(detail.overview())).isEmpty();
        assertThat(OverviewProbe.rendersHtmlDisabled(table, 0, 0)).isTrue();
        assertThat(OverviewProbe.rendersHtmlDisabled(table, 0, 2)).isTrue();
    }

    @Test
    void rightClickingAVariableRowOffersToAskTheServerAboutThatVariableOfThisTemplate() {
        detail.bindCompletion(PopupProbe.LABELLING_MENUS);
        detail.show(template(MOCK_TEMPLATE));

        JTable table = OverviewProbe.table(detail.overview());

        assertThat(PopupProbe.rightClickLabel(table, 1, table.getRowHeight() + 1))
                .contains("template repo://{owner}/{repo}/issues{?state,labels*} / repo");
        assertThat(PopupProbe.rightClickLabel(table, 1, table.getRowHeight() * 3 + 1))
                .contains("template repo://{owner}/{repo}/issues{?state,labels*} / labels");
        assertThat(PopupProbe.rightClickLabel(table, 1, table.getRowHeight() * 9))
                .isEmpty();
    }

    @Test
    void withoutCompletionBoundAVariableRowOffersNoMenu() {
        detail.show(template(MOCK_TEMPLATE));

        assertThat(PopupProbe.rightClickLabel(OverviewProbe.table(detail.overview()), 1, 1)).isEmpty();
    }

    private static McpResourceTemplateDefinition template(String raw) {
        JsonNode node = json(raw);
        return new McpResourceTemplateDefinition(node.path("uriTemplate").asText(""), node.path("name").asText(),
                node.path("description").asText(null), node.path("mimeType").asText(null), List.of(), node);
    }
}
