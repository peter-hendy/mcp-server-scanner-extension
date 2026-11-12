package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.UriTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.DetailSection;
import com.mcpscanner.ui.detail.FieldGrid;
import com.mcpscanner.ui.detail.RowMenu;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Optional;

public final class UriTemplateOverview {

    public static final String UNREADABLE_WARNING =
            "This is not a valid RFC 6570 URI template, so it is shown exactly as sent.";
    public static final String COMPLETION_NOTE =
            "Server advertises completions: right-click a variable and choose Ask server for values";

    private UriTemplateOverview() {
    }

    public static JComponent of(String uriTemplate, String mimeType, String description, JsonNode raw,
                                boolean completionsAdvertised, RowMenu variableMenu) {
        Optional<UriTemplate> parsed = UriTemplate.parse(uriTemplate);
        return OverviewPage.of(description, raw, List.of(
                DetailSection.of("URI template", templateView(uriTemplate, parsed)),
                variablesSection(parsed.map(PlacedVariables::of).orElse(List.of()), variableMenu),
                DetailSection.of("Details", details(mimeType, raw.path("annotations"), completionsAdvertised))));
    }

    private static JComponent templateView(String uriTemplate, Optional<UriTemplate> parsed) {
        if (uriTemplate == null || uriTemplate.isBlank()) {
            return hidden();
        }
        return parsed.map(UriTemplateView::highlighted)
                .orElseGet(() -> UriTemplateView.unreadable(uriTemplate, UNREADABLE_WARNING));
    }

    private static DetailSection variablesSection(List<PlacedVariable> variables, RowMenu variableMenu) {
        DetailSection section = new DetailSection("Variables (" + variables.size() + ")");
        section.show(variables.isEmpty() ? hidden() : TemplateVariablesTable.of(variables, variableMenu));
        return section;
    }

    private static FieldGrid details(String mimeType, JsonNode annotations, boolean completionsAdvertised) {
        FieldGrid grid = new FieldGrid();
        grid.addText("MIME type", mimeType);
        AnnotationFields.addTo(grid, annotations);
        if (completionsAdvertised) {
            grid.addText("Completion", COMPLETION_NOTE);
        }
        return grid;
    }

    private static JComponent hidden() {
        JPanel nothing = new JPanel();
        nothing.setVisible(false);
        return nothing;
    }
}
