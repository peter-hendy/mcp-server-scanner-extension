package com.mcpscanner.engine;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.mcpscanner.engine.EngineFixtures.issue;
import static org.assertj.core.api.Assertions.assertThat;

class SiteMapIssueSinkTest {

    private static final String NAME = "Command injection";
    private static final String URL = "https://mcp.example/mcp";

    private final FakeCheck keepingExisting = FakeCheck.named("check").consolidating(ConsolidationAction.KEEP_EXISTING);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void addsANewIssueToTheSiteMap() {
        FakeSiteMap siteMap = new FakeSiteMap();
        AuditIssue found = issue(NAME, URL);

        int reported = new SiteMapIssueSink(siteMap).report(keepingExisting::consolidateIssues, List.of(found)).size();

        assertThat(siteMap.added()).containsExactly(found);
        assertThat(reported).isEqualTo(1);
    }

    @Test
    void consolidatesIntoAnIssueBurpAlreadyHoldsAtTheSamePath() {
        assertConsolidated(URL, URL);
    }

    @Test
    void consolidatesIntoAnIssueAtTheSamePathWithADifferentQuery() {
        assertConsolidated(URL + "?session=1", URL + "?session=2");
    }

    @Test
    void consolidatesIntoAnIssueWhoseUrlSpellsOutTheDefaultPort() {
        assertConsolidated("https://mcp.example:443/mcp", URL);
    }

    @Test
    void keepsAnIssueAtADifferentPath() {
        assertKeptBeside(issue(NAME, "https://mcp.example/other"), issue(NAME, URL));
    }

    @Test
    void keepsAnIssueOnADifferentPort() {
        assertKeptBeside(issue(NAME, "https://mcp.example:8443/mcp"), issue(NAME, URL));
    }

    @Test
    void keepsAnIssueWithADifferentName() {
        assertKeptBeside(issue("Path traversal", URL), issue(NAME, URL));
    }

    @ParameterizedTest
    @EnumSource(value = ConsolidationAction.class, names = {"KEEP_BOTH", "KEEP_NEW"})
    void addsAnIssueTheCheckDoesNotConsolidate(ConsolidationAction action) {
        AuditIssue existing = issue(NAME, URL);
        AuditIssue found = issue(NAME, URL);
        FakeSiteMap siteMap = FakeSiteMap.holding(existing);

        new SiteMapIssueSink(siteMap).report(FakeCheck.named("check").consolidating(action)::consolidateIssues,
                List.of(found));

        assertThat(siteMap.added()).containsExactly(found);
    }

    @Test
    void reportsAgainAnIssueDeletedFromTheSiteMap() {
        FakeSiteMap siteMap = new FakeSiteMap();
        SiteMapIssueSink sink = new SiteMapIssueSink(siteMap);
        AuditIssue first = issue(NAME, URL);
        AuditIssue rescanned = issue(NAME, URL);

        sink.report(keepingExisting::consolidateIssues, List.of(first));
        siteMap.delete(first);
        sink.report(keepingExisting::consolidateIssues, List.of(rescanned));

        assertThat(siteMap.added()).containsExactly(first, rescanned);
    }

    private void assertConsolidated(String existingUrl, String newUrl) {
        FakeSiteMap siteMap = FakeSiteMap.holding(issue(NAME, existingUrl));

        int reported = new SiteMapIssueSink(siteMap).report(keepingExisting::consolidateIssues,
                List.of(issue(NAME, newUrl))).size();

        assertThat(siteMap.added()).isEmpty();
        assertThat(reported).isZero();
    }

    private void assertKeptBeside(AuditIssue existing, AuditIssue found) {
        FakeSiteMap siteMap = FakeSiteMap.holding(existing);

        new SiteMapIssueSink(siteMap).report(keepingExisting::consolidateIssues, List.of(found));

        assertThat(siteMap.added()).containsExactly(found);
    }
}
