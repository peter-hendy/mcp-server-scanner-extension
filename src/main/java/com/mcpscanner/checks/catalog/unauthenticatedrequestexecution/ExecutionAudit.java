package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.probe.ControlRefusal;
import com.mcpscanner.checks.shared.probe.IndependentSessionSender;
import com.mcpscanner.checks.shared.probe.ProbeExchange;

import java.util.List;
import java.util.Optional;

final class ExecutionAudit {

    private final AuthVariantSuite suite;
    private final IndependentSessionSender sender;
    private final ControlRefusal controlRefusal;
    private final AnonymousExecutionTier anonymousTier;
    private final InvalidCredentialTier invalidCredentialTier;
    private final BypassSweep bypassSweep;
    private final EnforcementEvidence enforcement;

    ExecutionAudit(AuthVariantSuite suite, IndependentSessionSender sender, ControlRefusal controlRefusal,
                   AnonymousExecutionTier anonymousTier, InvalidCredentialTier invalidCredentialTier,
                   BypassSweep bypassSweep, EnforcementEvidence enforcement) {
        this.suite = suite;
        this.sender = sender;
        this.controlRefusal = controlRefusal;
        this.anonymousTier = anonymousTier;
        this.invalidCredentialTier = invalidCredentialTier;
        this.bypassSweep = bypassSweep;
        this.enforcement = enforcement;
    }

    Optional<Finding> run(AuditSubject subject) {
        ProbeExchange anonymous = sendControl(subject, suite.noCredentialControl());
        Optional<Finding> anonymousFinding = anonymousTier.judge(subject, anonymous);
        if (anonymousFinding.isPresent()) {
            return anonymousFinding;
        }
        return controlRefusal.confirm(subject.baseline(), anonymous)
                .flatMap(refusedAnonymous -> afterAnonymousRefusal(subject, refusedAnonymous));
    }

    private Optional<Finding> afterAnonymousRefusal(AuditSubject subject, ProbeExchange refusedAnonymous) {
        enforcement.recordRefusal(subject, refusedAnonymous);
        ProbeExchange malformed = sendControl(subject, suite.malformedCredentialControl());
        Optional<ProbeExchange> refusedMalformed = controlRefusal.confirm(subject.baseline(), malformed);
        if (refusedMalformed.isEmpty()) {
            return invalidCredentialTier.judge(subject, refusedAnonymous, malformed);
        }
        return bypassSweep.sweep(subject, List.of(refusedAnonymous, refusedMalformed.get()));
    }

    private ProbeExchange sendControl(AuditSubject subject, AuthVariant control) {
        return sender.sendConfirmingRefusalInAFreshSession(control.candidate().applyTo(subject.seed()));
    }
}
