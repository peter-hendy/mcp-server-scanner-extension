package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.probe.ProbeExchange;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SweepMemo {

    private final SettledRejection settledRejection;
    private final InstantSource clock;
    private final Duration lifetime;
    private final Map<SweepScope, Map<SettledVariant, Instant>> rejected = new ConcurrentHashMap<>();

    SweepMemo(SettledRejection settledRejection, InstantSource clock, Duration lifetime) {
        this.settledRejection = settledRejection;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    List<AuthVariant> pending(AuditSubject subject, List<AuthVariant> variants) {
        Map<SettledVariant, Instant> rejectedHere = rejected.getOrDefault(SweepScope.of(subject), Map.of());
        return variants.stream()
                .filter(variant -> !current(rejectedHere.getOrDefault(SettledVariant.of(variant), Instant.MIN)))
                .toList();
    }

    void observe(AuditSubject subject, AuthVariant variant, ProbeExchange candidate,
                 List<ProbeExchange> refusedControls) {
        if (settledRejection.settles(variant, candidate, refusedControls)) {
            Map<SettledVariant, Instant> rejectedHere =
                    rejected.computeIfAbsent(SweepScope.of(subject), scope -> new ConcurrentHashMap<>());
            rejectedHere.values().removeIf(settledAt -> !current(settledAt));
            rejectedHere.put(SettledVariant.of(variant), clock.instant());
        }
    }

    void clear() {
        rejected.clear();
    }

    private boolean current(Instant settledAt) {
        return settledAt.isAfter(clock.instant().minus(lifetime));
    }
}
