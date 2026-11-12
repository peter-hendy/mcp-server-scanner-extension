package com.mcpscanner.engine;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.sitemap.SiteMapFilter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class SiteMapIssueSink {

    private final SiteMap siteMap;

    SiteMapIssueSink(SiteMap siteMap) {
        this.siteMap = siteMap;
    }

    synchronized List<AuditIssue> report(IssueConsolidation consolidation, List<AuditIssue> issues) {
        return issues.stream().filter(issue -> addUnlessConsolidated(consolidation, issue)).toList();
    }

    private boolean addUnlessConsolidated(IssueConsolidation consolidation, AuditIssue issue) {
        if (candidates(issue).anyMatch(existing -> keepsExisting(consolidation, existing, issue))) {
            return false;
        }
        siteMap.add(issue);
        return true;
    }

    private Stream<AuditIssue> candidates(AuditIssue issue) {
        IssueLocation location = IssueLocation.of(issue.baseUrl());
        return siteMap.issues(SiteMapFilter.prefixFilter(location.siteMapPrefix())).stream()
                .filter(existing -> Objects.equals(existing.name(), issue.name()))
                .filter(existing -> IssueLocation.of(existing.baseUrl()).equals(location));
    }

    private static boolean keepsExisting(IssueConsolidation consolidation, AuditIssue existing, AuditIssue issue) {
        return consolidation.consolidate(existing, issue) == ConsolidationAction.KEEP_EXISTING;
    }
}
