package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CheckRunner {

    private final Http http;
    private final InsertionPointSource insertionPoints;
    private final LateIssueSettlement lateIssues;

    public CheckRunner(Http http, InsertionPointSource insertionPoints) {
        this(http, insertionPoints, LateIssueSettlement.NONE);
    }

    public CheckRunner(Http http, InsertionPointSource insertionPoints, LateIssueSettlement lateIssues) {
        this.http = Objects.requireNonNull(http, "http must not be null");
        this.insertionPoints = Objects.requireNonNull(insertionPoints, "insertionPoints must not be null");
        this.lateIssues = Objects.requireNonNull(lateIssues, "lateIssues must not be null");
    }

    public List<AuditIssue> run(McpCheck check, List<HttpRequestResponse> allBaseRequests) {
        List<AuditIssue> issues = new ArrayList<>(runSynchronously(check, allBaseRequests));
        issues.addAll(lateIssues.settle(check));
        return List.copyOf(issues);
    }

    private List<AuditIssue> runSynchronously(McpCheck check, List<HttpRequestResponse> allBaseRequests) {
        List<HttpRequestResponse> baseRequests = allBaseRequests.stream()
                .filter(base -> ReplyRequirement.permits(check, base))
                .toList();
        return check.accept(new CheckVisitor<>() {
            @Override
            public List<AuditIssue> visitInsertionPointCheck(InsertionPointCheck insertionPointCheck) {
                return baseRequests.stream()
                        .flatMap(base -> insertionPoints.insertionPointsFor(base).stream()
                                .flatMap(point -> insertionPointCheck.audit(base, point, http).stream()))
                        .toList();
            }

            @Override
            public List<AuditIssue> visitRequestCheck(RequestCheck requestCheck) {
                return baseRequests.stream()
                        .flatMap(base -> requestCheck.audit(base, http).stream())
                        .toList();
            }

            @Override
            public List<AuditIssue> visitHostCheck(HostCheck hostCheck) {
                HostAudits audits = new HostAudits(hostCheck);
                return baseRequests.stream()
                        .flatMap(base -> audits.audit(base, http).stream())
                        .toList();
            }
        });
    }
}
