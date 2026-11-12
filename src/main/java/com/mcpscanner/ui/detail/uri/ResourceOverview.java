package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.DetailSection;
import com.mcpscanner.ui.detail.FieldGrid;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

public final class ResourceOverview {

    private static final int GAP = 8;

    private ResourceOverview() {
    }

    public static JComponent of(String uri, String mimeType, String description, JsonNode raw) {
        return OverviewPage.of(description, raw, List.of(
                DetailSection.of("Resource", resourceFields(uri, mimeType, raw.path("size"))),
                DetailSection.of("Annotations", annotationFields(raw.path("annotations")))));
    }

    private static FieldGrid resourceFields(String uri, String mimeType, JsonNode size) {
        FieldGrid grid = new FieldGrid();
        if (uri != null && !uri.isBlank()) {
            grid.addComponent("URI", uriWithScheme(uri));
        }
        grid.addText("MIME type", mimeType);
        if (!size.isMissingNode() && !size.isNull()) {
            grid.addText("Size", ByteSizeText.of(size));
        }
        return grid;
    }

    private static FieldGrid annotationFields(JsonNode annotations) {
        FieldGrid grid = new FieldGrid();
        AnnotationFields.addTo(grid, annotations);
        return grid;
    }

    private static JComponent uriWithScheme(String uri) {
        JPanel row = new JPanel(new BorderLayout(GAP, 0));
        row.setOpaque(false);
        row.add(UiStyle.monospaced(new WrappingText(uri)), BorderLayout.CENTER);
        String scheme = UriScheme.of(uri);
        if (!scheme.isEmpty()) {
            row.add(Chip.neutral("scheme: " + scheme), BorderLayout.EAST);
        }
        return row;
    }
}
