package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;

import java.util.Optional;

public final class NonexistentRouteControl {

    private final IndependentSessionSender sender;
    private final BaselineEquivalenceOracle oracle;

    public NonexistentRouteControl(IndependentSessionSender sender, BaselineEquivalenceOracle oracle) {
        this.sender = sender;
        this.oracle = oracle;
    }

    public Optional<ProbeExchange> sendFor(Optional<RequestMutation> control, HttpRequest seed) {
        return control.map(mutation -> sender.send(mutation.applyTo(seed)));
    }

    public boolean suppresses(Optional<ProbeExchange> control) {
        return control.isPresent() && !oracle.refused(control.get().last());
    }
}
