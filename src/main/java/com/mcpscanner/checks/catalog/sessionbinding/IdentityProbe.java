package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.crossidentity.AnonymousReply;
import com.mcpscanner.checks.api.crossidentity.IdentityIssue;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.OwnerResult;
import com.mcpscanner.checks.api.crossidentity.ProbeKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class IdentityProbe {

    private final ProbeKit kit;
    private final Principal principal;
    private final OwnerResult ownerResult;
    private final AnonymousReply anonymous;
    private final List<IdentityIssue> issues = new ArrayList<>();

    IdentityProbe(ProbeKit kit, Principal principal, OperationReply owner, AnonymousReply anonymous) {
        this.kit = kit;
        this.principal = principal;
        this.ownerResult = new OwnerResult(kit, owner);
        this.anonymous = anonymous;
    }

    IdentityOutcome run() {
        Optional<OperationReply> own = kit.sessions().fresh(asPrincipal()).map(this::reply);
        Verdict verdict = anonymousAccountedFor(own) && distinguishesIdentity(own) ? new SessionProbes(this).run()
                : settled(Verdict.UNSUITABLE_SEED);
        return new IdentityOutcome(principal, verdict, issues);
    }

    ProbeKit kit() {
        return kit;
    }

    Principal principal() {
        return principal;
    }

    OperationReply owner() {
        return ownerResult.owner();
    }

    AnonymousReply anonymous() {
        return anonymous;
    }

    HttpRequest asPrincipal() {
        return kit.requests().as(principal, kit.seed().request());
    }

    OperationReply reply(HttpRequest request) {
        return kit.reply(request);
    }

    boolean ownerResultReturned(String probe, OperationReply reply) {
        return ownerResult.returnedBy(probe, reply);
    }

    boolean ownerMarkerConfirms(OperationReply reply) {
        return ownerResult.markerConfirmedBy(reply);
    }

    void decide(String decision) {
        kit.log().record(decision);
    }

    void skip(SkipReason reason) {
        kit.skippedUnlessStopped(reason);
    }

    void report(IdentityIssue issue) {
        issues.add(issue);
    }

    Verdict settled(Verdict unlessStopped) {
        return kit.transport().stopped() && issues.isEmpty() ? Verdict.INCONCLUSIVE : unlessStopped;
    }

    private boolean distinguishesIdentity(Optional<OperationReply> own) {
        boolean ownerResult = own.filter(reply -> owner().sameResult(reply)).isPresent();
        if (ownerResult) {
            decide("session_binding:identity_gets_owner_result");
            skip(SkipReason.notApplicable("the identity's own session already gets the owner's result"));
        }
        return !ownerResult;
    }

    private boolean anonymousAccountedFor(Optional<OperationReply> own) {
        if (anonymous.refused()) {
            return true;
        }
        boolean served = own.filter(reply -> anonymous.reply().sameResult(reply)).isPresent();
        decide(served ? "anonymous_served_as_peer" : "anonymous_operation_success");
        if (!served) {
            skip(SkipReason.notApplicable("the anonymous request succeeds with a result the identity does not get"));
        }
        return served;
    }
}
