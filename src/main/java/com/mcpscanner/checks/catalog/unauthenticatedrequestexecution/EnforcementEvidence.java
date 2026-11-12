package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.HostKey;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.probe.ProbeExchange;
import com.mcpscanner.checks.shared.probe.ServerErrorAnswer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class EnforcementEvidence {

    private final BaselineEquivalenceOracle oracle;
    private final Map<HostKey, HttpRequestResponse> refusals = new ConcurrentHashMap<>();
    private final Map<HostKey, CompletableFuture<Optional<HttpRequestResponse>>> probes = new ConcurrentHashMap<>();

    EnforcementEvidence(BaselineEquivalenceOracle oracle) {
        this.oracle = oracle;
    }

    void recordRefusal(AuditSubject subject, ProbeExchange refusal) {
        if (oracle.successful(subject.baseline()) && !ServerErrorAnswer.of(refusal.last())) {
            refusals.putIfAbsent(subject.host(), refusal.last());
        }
    }

    Optional<HttpRequestResponse> find(AuditSubject subject, EnforcementProbe probe) {
        HostKey host = subject.host();
        Optional<HttpRequestResponse> refusal = Optional.ofNullable(refusals.get(host));
        return refusal.isPresent() ? refusal : probed(subject, probe).or(() -> Optional.ofNullable(refusals.get(host)));
    }

    void clear() {
        refusals.clear();
        probes.clear();
    }

    private Optional<HttpRequestResponse> probed(AuditSubject subject, EnforcementProbe probe) {
        CompletableFuture<Optional<HttpRequestResponse>> claim = new CompletableFuture<>();
        CompletableFuture<Optional<HttpRequestResponse>> running = probes.putIfAbsent(subject.host(), claim);
        if (running != null) {
            return running.join();
        }
        try {
            claim.complete(probe.observe(subject));
        } catch (RuntimeException failure) {
            probes.remove(subject.host(), claim);
            claim.complete(Optional.empty());
        }
        return claim.join();
    }
}
