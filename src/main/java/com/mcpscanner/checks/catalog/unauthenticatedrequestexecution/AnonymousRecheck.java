package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.probe.ControlRefusal;
import com.mcpscanner.checks.shared.probe.IndependentSessionSender;
import com.mcpscanner.checks.shared.probe.ProbeExchange;

import java.util.Optional;

final class AnonymousRecheck {

    private final AuthVariantSuite suite;
    private final IndependentSessionSender sender;
    private final ControlRefusal controlRefusal;

    AnonymousRecheck(AuthVariantSuite suite, IndependentSessionSender sender, ControlRefusal controlRefusal) {
        this.suite = suite;
        this.sender = sender;
        this.controlRefusal = controlRefusal;
    }

    Optional<ProbeExchange> stillRefused(AuditSubject subject) {
        return controlRefusal.confirm(subject.baseline(), sender.sendConfirmingRefusalInAFreshSession(
                suite.noCredentialControl().candidate().applyTo(subject.seed())));
    }
}
