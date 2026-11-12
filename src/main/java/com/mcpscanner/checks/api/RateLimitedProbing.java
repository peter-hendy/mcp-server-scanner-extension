package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;
import java.util.function.Function;

public final class RateLimitedProbing {

    private final CheckLog log;

    public RateLimitedProbing(CheckLog log) {
        this.log = log;
    }

    public List<AuditIssue> run(String subject, Http http, Function<Http, List<AuditIssue>> probing) {
        WithholdingAfterTooManyRequests guarded = new WithholdingAfterTooManyRequests(http);
        List<AuditIssue> issues = probing.apply(guarded);
        if (guarded.tripped()) {
            log.notRun(subject, SkipReason.rateLimited());
        }
        return issues;
    }
}
