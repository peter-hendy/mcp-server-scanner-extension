package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.checks.api.CheckLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

final class ProbeSessionPool<S extends AutoCloseable> implements AutoCloseable {

    private final Supplier<S> opener;
    private final Function<S, Optional<String>> sessionIds;
    private final ProbeTraffic traffic;
    private final CheckLog log;
    private final Set<String> claimedIds = new HashSet<>();
    private final List<String> issuedIds = new ArrayList<>();
    private final List<S> owned = new ArrayList<>();
    private final List<S> unowned = new ArrayList<>();
    private final boolean closesUnowned;
    private Instant firstRequested;
    private Instant lastAnswered;
    private boolean exhausted;

    private ProbeSessionPool(Supplier<S> opener, Function<S, Optional<String>> sessionIds, ProbeTraffic traffic,
                             String liveSessionId, CheckLog log, boolean closesUnowned) {
        this.closesUnowned = closesUnowned;
        this.opener = opener;
        this.sessionIds = sessionIds;
        this.traffic = traffic;
        this.log = log;
        claimedIds.add(liveSessionId);
    }

    static <S extends AutoCloseable> ProbeSessionPool<S> closingOwnedOnly(
            Supplier<S> opener, Function<S, Optional<String>> sessionIds, ProbeTraffic traffic, String liveSessionId,
            CheckLog log) {
        return new ProbeSessionPool<>(opener, sessionIds, traffic, liveSessionId, log, false);
    }

    static <S extends AutoCloseable> ProbeSessionPool<S> closingEverySession(
            Supplier<S> opener, Function<S, Optional<String>> sessionIds, ProbeTraffic traffic, String liveSessionId,
            CheckLog log) {
        return new ProbeSessionPool<>(opener, sessionIds, traffic, liveSessionId, log, true);
    }

    OpenedProbes<S> openUpTo(int total) {
        if (firstRequested == null) {
            firstRequested = Instant.now();
        }
        while (!exhausted && issuedIds.size() < total && !traffic.throttled()) {
            Optional<String> issued = openOne().flatMap(this::keep);
            lastAnswered = Instant.now();
            issued.ifPresentOrElse(issuedIds::add, () -> exhausted = true);
        }
        return new OpenedProbes<>(List.copyOf(issuedIds), List.copyOf(owned), firstRequested,
                lastAnswered == null ? firstRequested : lastAnswered);
    }

    private Optional<String> keep(S session) {
        Optional<String> id = sessionIds.apply(session);
        id.filter(claimedIds::add).ifPresentOrElse(unseen -> owned.add(session), () -> unowned.add(session));
        return id;
    }

    private Optional<S> openOne() {
        try {
            return Optional.of(opener.get());
        } catch (RuntimeException refused) {
            log.info("probe session " + (issuedIds.size() + 1) + " could not be opened: " + refused.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        owned.forEach(session -> closeQuietly(session, sessionIds.apply(session).orElse("?")));
        if (closesUnowned) {
            unowned.forEach(session -> closeQuietly(session, sessionIds.apply(session).orElse("?")));
        }
    }

    private void closeQuietly(S session, String id) {
        try {
            session.close();
        } catch (Exception failed) {
            log.warn("closing probe session " + id + " failed: " + failed.getMessage());
        }
    }
}
