package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.auth.CredentialSubstitution;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.stream.EventStreams;
import com.mcpscanner.checks.shared.stream.StreamOpening;
import com.mcpscanner.mcp.McpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

final class StreamAccessTier {

    static final String VARIANT_NAME = "stream.credential.none";

    private final EventStreams streams;
    private final CredentialHeaders credentials;
    private final CredentialSubstitution substitution;
    private final EnforcementEvidence enforcement;
    private final ScopeClaims judged;

    StreamAccessTier(EventStreams streams, CredentialHeaders credentials, CredentialSubstitution substitution,
                     EnforcementEvidence enforcement, ScopeClaims judged) {
        this.streams = streams;
        this.credentials = credentials;
        this.substitution = substitution;
        this.enforcement = enforcement;
        this.judged = judged;
    }

    Optional<Finding> judge(AuditSubject subject, Http http, ProbeChannel budget,
                            EnforcementProbe enforcementProbe) {
        SweepScope scope = SweepScope.eventStreamOf(subject);
        if (!subject.credentialsConfigured() || !judged.claim(scope)) {
            return Optional.empty();
        }
        Attempt attempt = new Attempt(subject, http, budget);
        Optional<StreamOpening> authenticated = attempt.open(credentials::withMissingAdded);
        Optional<StreamOpening> anonymous = authenticated.filter(StreamOpening::opened)
                .flatMap(opened -> attempt.open(substitution::anonymous));
        Optional<HttpRequestResponse> enforced = anonymous.filter(StreamOpening::opened)
                .flatMap(opened -> enforcement.find(subject, enforcementProbe));
        if (enforced.isPresent()) {
            return Optional.of(finding(subject, authenticated.orElseThrow(), anonymous.orElseThrow(),
                    enforced.get()));
        }
        if (Stream.of(authenticated, anonymous).flatMap(Optional::stream).anyMatch(StreamOpening::transientFailure)
                || anonymous.filter(StreamOpening::opened).isPresent()) {
            judged.releaseForRetry(scope);
        }
        return Optional.empty();
    }

    private final class Attempt {

        private final AuditSubject subject;
        private final Http http;
        private final ProbeChannel budget;

        Attempt(AuditSubject subject, Http http, ProbeChannel budget) {
            this.subject = subject;
            this.http = http;
            this.budget = budget;
        }

        Optional<StreamOpening> open(UnaryOperator<HttpRequest> shaping) {
            return streams.open(subject.baseline(), http, budget, shaping);
        }
    }

    private static Finding finding(AuditSubject subject, StreamOpening authenticated, StreamOpening anonymous,
                                   HttpRequestResponse enforced) {
        boolean sessionBound = anonymous.exchange().request().hasHeader(McpHeaders.SESSION_ID);
        List<HttpRequestResponse> evidence = new ArrayList<>(List.of(authenticated.exchange(),
                anonymous.exchange(), enforced));
        return new Finding(sessionBound ? FindingKind.SESSION_STREAM : FindingKind.ANONYMOUS_STREAM, subject,
                VARIANT_NAME, sessionBound ? McpHeaders.SESSION_ID + " kept" : "", evidence);
    }
}
