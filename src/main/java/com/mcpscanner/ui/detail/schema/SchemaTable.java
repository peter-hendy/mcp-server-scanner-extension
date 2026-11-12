package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class SchemaTable extends JPanel {

    private static final int SECTION_GAP = 10;

    private final String heading;
    private SchemaOutline outline = SchemaOutline.of(null);

    public SchemaTable(String heading) {
        super(new BorderLayout(0, SECTION_GAP));
        this.heading = heading;
        setOpaque(false);
    }

    public void show(JsonNode schema, String protocolVersion) {
        outline = SchemaOutline.of(schema);
        removeAll();
        add(new SchemaHeader(heading, outline.summary().text(), SchemaDialect.label(schema, protocolVersion)),
                BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        if (!outline.otherKeywords().isEmpty()) {
            add(new OtherKeywordsBox(outline.otherKeywords()), BorderLayout.SOUTH);
        }
        revalidate();
        repaint();
    }

    public int parameterCount() {
        return outline.parameterCount();
    }

    public boolean isEmpty() {
        return outline.isEmpty();
    }

    public boolean hasContent() {
        return outline.hasContent();
    }

    private JComponent body() {
        return outline.isEmpty()
                ? SchemaLabels.placeholder("No properties declared.")
                : topAligned(new SchemaGrid(outline.rows()));
    }

    private static JComponent topAligned(JComponent content) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(content, BorderLayout.NORTH);
        return holder;
    }
}
