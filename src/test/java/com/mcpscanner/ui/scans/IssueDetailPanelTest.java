package com.mcpscanner.ui.scans;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JEditorPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IssueDetailPanelTest {

    private final FakeEditors editors = new FakeEditors();
    private final IssueDetailPanel panel = new IssueDetailPanel(editors);
    private final JTabbedPane tabs = Find.named(panel, JTabbedPane.class, IssueDetailPanel.TABS);

    @AfterEach
    void restore() {
        UIManager.put("Colors.ui.text.link", null);
    }

    @Test
    void advisoryLinksFollowBurpsLinkColourThroughThePanesStyleSheet() {
        JEditorPane advisory = Find.named(panel, JEditorPane.class, IssueDetailPanel.ADVISORY);
        UIManager.put("Colors.ui.text.link", new Color(0xAD5B28));
        panel.show(Issues.issue("Open redirect", AuditIssueSeverity.LOW,
                "<a href=\"https://cwe.mitre.org/\">CWE-601</a>", List.of()));
        UIManager.put("Colors.ui.text.link", new Color(0xFF9E7A));

        panel.show(Issues.issue("Open redirect", AuditIssueSeverity.LOW,
                "<a href=\"https://cwe.mitre.org/\">CWE-601</a>", List.of()));

        StyleSheet styles = ((HTMLDocument) advisory.getDocument()).getStyleSheet();
        assertThat(styles.getForeground(styles.getRule("a"))).isEqualTo(new Color(0xFF9E7A));
        assertThat(advisory.getText()).contains("CWE-601").doesNotContain("style=");
    }

    @Test
    void showsTheAdvisoryAndTheTargetsExchangeReadOnly() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);

        panel.show(Issues.issue("OS command injection", AuditIssueSeverity.HIGH, "<p>ran id</p>",
                List.of(Issues.exchange(request, response))));

        assertThat(Find.named(panel, JEditorPane.class, IssueDetailPanel.ADVISORY).getText())
                .contains("OS command injection", "ran id");
        assertThat(ComponentSearch.titles(tabs)).containsExactly("Advisory", "Request", "Response");
        assertThat(tabs.isEnabledAt(1)).isTrue();
        assertThat(tabs.isEnabledAt(2)).isTrue();
        verify(editors.request).setRequest(request);
        verify(editors.response).setResponse(response);
    }

    @Test
    void anIssueWithoutEvidenceShowsOnlyItsAdvisory() {
        panel.show(Issues.issue("Session ID guessable", AuditIssueSeverity.LOW));

        assertThat(tabs.isEnabledAt(1)).isFalse();
        assertThat(tabs.isEnabledAt(2)).isFalse();
    }
}
