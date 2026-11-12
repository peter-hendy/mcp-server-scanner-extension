package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grant.RefreshGrantStore;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.redaction.ExchangeRedactor;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.TooManyRequests;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class RefreshGrantAudit {

    static final SkipReason NO_LIVE_GRANT =
            SkipReason.prerequisiteMissing("the OAuth session holds no live refresh grant");
    private static final int NO_RESPONSE = 0;

    private final Http http;
    private final HttpRequestResponse hostBase;
    private final Supplier<AuthStrategy> authStrategy;
    private final AuthorizationServerMetadata metadata;
    private final CheckLog log;

    RefreshGrantAudit(Http http, HttpRequestResponse hostBase, Supplier<AuthStrategy> authStrategy,
                      AuthorizationServerMetadata metadata, CheckLog log) {
        this.http = http;
        this.hostBase = hostBase;
        this.authStrategy = authStrategy;
        this.metadata = metadata;
        this.log = log;
    }

    List<AuditIssue> run(RefreshGrantStore store) {
        RefreshExchanger exchanger = new RefreshExchanger(http, new RefreshRequestFactory(metadata.tokenEndpoint()));
        TokenUsability usability = new TokenUsability(http, hostBase.request(), authStrategy);
        Optional<RecordedEvidence> recorded = store.withExclusiveGrant(live -> {
            usability.primeWithLiveSession();
            SecretRecordingGrant recording = new SecretRecordingGrant(live);
            RefreshEvidence evidence = new RefreshGrantProbe(exchanger, metadata, usability::judge, log)
                    .run(recording);
            recording.recordCurrentGrant();
            return new RecordedEvidence(evidence, recording.secrets());
        });
        recorded.ifPresentOrElse(evidence -> logOutcome(evidence.evidence()),
                () -> log.notRun(subject(), NO_LIVE_GRANT));
        return recorded.map(evidence -> issues(evidence.evidence(), new ExchangeRedactor(evidence.secrets())))
                .orElse(List.of());
    }

    private void logUnissuedControl(RefreshExchange control) {
        switch (control.status()) {
            case TooManyRequests.STATUS -> log.notRun(subject(), SkipReason.rateLimited());
            case NO_RESPONSE -> log.notRun(subject(),
                    SkipReason.transportFailure("the control refresh of the live grant got no response"));
            default -> log.notRun(subject(), SkipReason.prerequisiteMissing(
                    "the control refresh of the live grant was refused with HTTP " + control.status()));
        }
    }

    private String subject() {
        return hostBase.request().url();
    }

    private record RecordedEvidence(RefreshEvidence evidence, Set<String> secrets) {
    }

    private List<AuditIssue> issues(RefreshEvidence evidence, ExchangeRedactor redactor) {
        RefreshFindings findings = new RefreshFindings(metadata);
        OAuthRefreshGrantIssues issues = new OAuthRefreshGrantIssues(McpRequestDetector.extractBaseUrl(hostBase), redactor);
        return Stream.concat(
                findings.clientAuthentication(evidence).map(issues::clientAuthentication).stream(),
                findings.scopeExpansion(evidence).map(issues::scopeExpansion).stream()).toList();
    }

    private void logOutcome(RefreshEvidence evidence) {
        if (!evidence.control().issued()) {
            logUnissuedControl(evidence.control());
        }
        log.info("client=" + evidence.clientType()
                + " control=" + evidence.control().status()
                + " unissued=" + evidence.unissuedControl().map(RefreshExchange::status).orElse(0)
                + " unauthenticated=" + evidence.unauthenticatedAttempts().stream()
                .map(attempt -> attempt.exchange().authentication() + ":" + attempt.exchange().status()).toList()
                + " scope=" + evidence.scopeProbe()
                .map(probe -> probe.extraScope() + ":" + probe.refresh().exchange().status()
                        + (probe.expanded() ? ":expanded" : "") + (probe.restored() ? ":restored" : ""))
                .orElse("not-probed"));
    }
}
