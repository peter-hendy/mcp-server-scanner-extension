package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.collaborator.InteractionListener;
import com.mcpscanner.checks.shared.collaborator.ObservedInteractions;
import com.mcpscanner.checks.shared.collaborator.WatchDecision;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class WatchedField implements InteractionListener {

    private final HttpRequestResponse base;
    private final ProbedField field;
    private final List<SentProbe> sent;
    private final ConfirmedFields confirmed;
    private final OutboundRequestIssues issues;
    private final Consumer<OutboundFinding> reporter;

    WatchedField(HttpRequestResponse base, ProbedField field, List<SentProbe> sent, ConfirmedFields confirmed,
                 OutboundRequestIssues issues, Consumer<OutboundFinding> reporter) {
        this.base = base;
        this.field = field;
        this.sent = List.copyOf(sent);
        this.confirmed = confirmed;
        this.issues = issues;
        this.reporter = reporter;
    }

    @Override
    public WatchDecision interacted(ObservedInteractions soFar) {
        Optional<OutboundFinding> finding = issues.findingFor(field, sent, soFar, base);
        finding.filter(found -> confirmed.recordIfStronger(FieldSubject.of(base.request(), field), found))
                .ifPresent(reporter);
        return finding.filter(OutboundFinding::settles).isPresent()
                ? WatchDecision.DONE
                : WatchDecision.KEEP_WATCHING;
    }
}
