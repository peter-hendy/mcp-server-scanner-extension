package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisoryHtmlTest {

    @Test
    void describesTheIssueWithItsRatingUrlDetailRemediationAndBackground() {
        String html = AdvisoryHtml.of(Issues.issue("File path traversal", AuditIssueSeverity.HIGH));

        assertThat(html).contains("File path traversal", "High", "Firm", "https://mcp.example/mcp",
                "detail of File path traversal", "fix File path traversal", "background of File path traversal");
    }

    @Test
    void dropsMarkupThatCouldReachTheNetwork() {
        String html = AdvisoryHtml.of(Issues.issue("Beacon", AuditIssueSeverity.LOW,
                "<p>ok</p><img src=\"http://evil.example/x.png\"><link rel=\"stylesheet\" href=\"http://evil.example/s.css\">"
                        + "<style>@import url(http://evil.example/i.css);</style>",
                List.of()));

        assertThat(html).contains("ok").doesNotContain("evil.example", "<img", "<link", "<style");
    }

    @Test
    void escapesTheIssueName() {
        String html = AdvisoryHtml.of(Issues.issue("<b>name</b>", AuditIssueSeverity.INFORMATION));

        assertThat(html).contains("&lt;b&gt;name&lt;/b&gt;");
    }

    @Test
    void capsAVeryLargeDetailSoTheEdtNeverParsesItWhole() {
        String huge = "<p>" + "x".repeat(AdvisoryHtml.MAX_SECTION_CHARS * 3) + "</p>";

        String html = AdvisoryHtml.of(Issues.issue("Large", AuditIssueSeverity.LOW, huge, List.of()));

        assertThat(html.length()).isLessThan(AdvisoryHtml.MAX_SECTION_CHARS * 2);
        assertThat(html).contains("(truncated)");
    }

    @Test
    void anUnratedIssueStillRenders() {
        String html = AdvisoryHtml.of(Issues.issue("Unrated", null));

        assertThat(html).contains("Unrated");
    }
}
