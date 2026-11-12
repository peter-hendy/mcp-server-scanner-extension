package com.mcpscanner.ui;

import com.mcpscanner.client.ListOutcome;
import com.mcpscanner.ui.widgets.WarningBanner;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

final class ListStatusBanner extends JPanel {

    private static final int GAP = 8;

    private final String listMethod;
    private final WarningBanner banner = new WarningBanner("");

    ListStatusBanner(String listMethod) {
        super(new BorderLayout());
        this.listMethod = listMethod;
        setBorder(BorderFactory.createEmptyBorder(GAP, GAP, 0, GAP));
        add(banner, BorderLayout.CENTER);
        setVisible(false);
    }

    void show(ListOutcome<?> outcome) {
        List<String> messages = new ArrayList<>();
        outcome.error().ifPresent(error -> messages.add(listMethod + " failed: " + error.describe()));
        outcome.truncation().ifPresent(truncation -> messages.add("List incomplete: " + truncation.describe()));
        banner.setMessage(String.join(" · ", messages));
        setVisible(!messages.isEmpty());
    }

    String message() {
        return banner.message();
    }
}
