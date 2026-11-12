package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.net.URI;
import java.util.function.Consumer;

final class SupportLinks {

    private static final String ISSUES = "https://github.com/peter-hendy/mcp-server-scanner-extension/issues/new";

    private SupportLinks() {
    }

    static final String REPORT_BUG = "Report bug";
    static final String REQUEST_FEATURE = "Request feature";

    static JPanel create(Consumer<Throwable> errorLog) {
        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        links.add(new HyperlinkLabel(REPORT_BUG, URI.create(ISSUES + "?labels=bug"), errorLog));
        links.add(separator());
        links.add(new HyperlinkLabel(REQUEST_FEATURE, URI.create(ISSUES + "?labels=enhancement"), errorLog));
        return links;
    }

    private static JLabel separator() {
        JLabel separator = new JLabel(" · ");
        separator.setForeground(ThemeColors.muted());
        return separator;
    }
}
