package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.AnonymousReply;
import com.mcpscanner.checks.api.crossidentity.DecisionLog;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.OwnerResult;
import com.mcpscanner.checks.api.crossidentity.ProbeKit;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import com.mcpscanner.checks.api.Principal;

import java.util.List;
import java.util.function.Consumer;

record PrincipalProbe(ProbeKit kit, Principal principal, OperationReply owner, AnonymousReply anonymous,
                      Consumer<Finding> findings) {

    DecisionLog log() {
        return kit.log();
    }

    OperationReply reply(HttpRequest request) {
        return kit.reply(request);
    }

    HttpRequest asPrincipal() {
        return kit.requests().as(principal, kit.seed().request());
    }

    HttpRequest ownerRequest() {
        return owner.exchange().request();
    }

    boolean ownerResultReturned(String probe, OperationReply reply) {
        return new OwnerResult(kit, owner).returnedBy(probe, reply);
    }

    boolean ownerMarkerConfirms(OperationReply reply) {
        return new OwnerResult(kit, owner).markerConfirmedBy(reply);
    }

    void decide(String decision) {
        log().record(decision);
    }

    boolean rejected(String decision) {
        decide(decision);
        return false;
    }

    void report(FindingKind kind, String family, boolean firm, String observation,
                List<HttpRequestResponse> evidence) {
        findings.accept(new Finding(kind, family, firm ? AuditIssueConfidence.FIRM : AuditIssueConfidence.TENTATIVE,
                observation, principal, evidence));
    }
}
