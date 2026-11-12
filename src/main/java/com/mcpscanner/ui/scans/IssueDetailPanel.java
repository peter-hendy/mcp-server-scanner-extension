package com.mcpscanner.ui.scans;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.text.html.HTMLDocument;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Optional;

final class IssueDetailPanel extends JPanel {

    static final String ADVISORY = "issue-advisory";
    static final String TABS = "issue-detail-tabs";

    private static final int REQUEST_TAB = 1;
    private static final int RESPONSE_TAB = 2;
    private static final Dimension PREFERRED = new Dimension(520, 360);

    private final JEditorPane advisory = new JEditorPane("text/html", "");
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final JTabbedPane tabs = new JTabbedPane();

    IssueDetailPanel(MessageEditors editors) {
        super(new BorderLayout());
        this.requestEditor = editors.requestEditor();
        this.responseEditor = editors.responseEditor();
        advisory.setName(ADVISORY);
        advisory.setEditable(false);
        tabs.setName(TABS);
        tabs.addTab("Advisory", new JScrollPane(advisory));
        tabs.addTab("Request", requestEditor.uiComponent());
        tabs.addTab("Response", responseEditor.uiComponent());
        setPreferredSize(PREFERRED);
        add(tabs, BorderLayout.CENTER);
    }

    void show(AuditIssue issue) {
        styleLinks();
        advisory.setText(AdvisoryHtml.of(issue));
        advisory.setCaretPosition(0);
        Optional<HttpRequestResponse> exchange = Evidence.first(issue);
        exchange.ifPresent(this::showExchange);
        tabs.setEnabledAt(REQUEST_TAB, exchange.isPresent());
        tabs.setEnabledAt(RESPONSE_TAB, exchange.filter(HttpRequestResponse::hasResponse).isPresent());
        if (!tabs.isEnabledAt(tabs.getSelectedIndex())) {
            tabs.setSelectedIndex(0);
        }
    }

    private void styleLinks() {
        int rgb = ThemeColors.link().getRGB() & 0xFFFFFF;
        ((HTMLDocument) advisory.getDocument()).getStyleSheet().addRule(String.format("a { color: #%06x }", rgb));
    }

    private void showExchange(HttpRequestResponse exchange) {
        requestEditor.setRequest(exchange.request());
        if (exchange.hasResponse()) {
            responseEditor.setResponse(exchange.response());
        }
    }
}
