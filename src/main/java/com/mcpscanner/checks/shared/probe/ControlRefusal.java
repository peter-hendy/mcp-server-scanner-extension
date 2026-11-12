package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;

import java.util.Optional;

public final class ControlRefusal {

    private final ProbeChannel channel;
    private final BaselineEquivalenceOracle oracle;

    public ControlRefusal(ProbeChannel channel, BaselineEquivalenceOracle oracle) {
        this.channel = channel;
        this.oracle = oracle;
    }

    public Optional<ProbeExchange> confirm(HttpRequestResponse baseline, ProbeExchange control) {
        HttpRequestResponse answer = control.last();
        if (oracle.refusedByAuthentication(answer)) {
            return Optional.of(control);
        }
        if (!ServerErrorAnswer.of(answer) || !oracle.successful(baseline)) {
            return Optional.empty();
        }
        HttpRequestResponse repeat = channel.send(answer.request());
        return ServerErrorAnswer.reproduces(answer, repeat)
                ? Optional.of(control.followedBy(repeat)) : Optional.empty();
    }
}
