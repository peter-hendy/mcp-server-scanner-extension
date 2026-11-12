package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.widgets.RevealToggle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;

final class MaskedJsonView extends JPanel {

    private static final int PADDING = 8;

    private final RevealToggle reveal = new RevealToggle("secrets");
    private final RawJsonView json;
    private final JsonNode document;
    private final JsonNode masked;

    MaskedJsonView(String hint, JsonNode document) {
        super(new BorderLayout());
        this.json = new RawJsonView(hint);
        this.document = document;
        this.masked = RegistrationSecrets.masked(document);
        reveal.addItemListener(event -> showDocument());
        add(toolbar(), BorderLayout.NORTH);
        add(json, BorderLayout.CENTER);
        showDocument();
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, 0, PADDING));
        toolbar.add(reveal);
        return toolbar;
    }

    private void showDocument() {
        json.show(reveal.isSelected() ? document : masked);
    }
}
