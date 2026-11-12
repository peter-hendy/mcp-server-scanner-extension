package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueDefinition;
import com.mcpscanner.ui.widgets.SeverityLabels;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import java.util.Optional;

final class AdvisoryHtml {

    static final int MAX_SECTION_CHARS = 100_000;
    private static final Safelist OFFLINE_MARKUP = Safelist.basic().addTags("h3", "h4");

    private AdvisoryHtml() {
    }

    static String of(AuditIssue issue) {
        String advisory = "<h3>" + Entities.escape(issue.name()) + "</h3>"
                + "<p>Severity: " + SeverityLabels.of(issue.severity())
                + "<br>Confidence: " + SeverityLabels.of(issue.confidence())
                + "<br>URL: " + Entities.escape(issue.baseUrl()) + "</p>"
                + section("Issue detail", issue.detail())
                + section("Remediation", issue.remediation())
                + section("Issue background",
                        Optional.ofNullable(issue.definition()).map(AuditIssueDefinition::background).orElse(null));
        return Jsoup.clean(advisory, OFFLINE_MARKUP);
    }

    private static String section(String heading, String html) {
        return html == null || html.isBlank() ? "" : "<h4>" + heading + "</h4>" + capped(html);
    }

    private static String capped(String html) {
        return html.length() <= MAX_SECTION_CHARS ? html
                : html.substring(0, MAX_SECTION_CHARS) + "<p>… (truncated)</p>";
    }
}
