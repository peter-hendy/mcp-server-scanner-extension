package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.auth.grant.LiveRefreshGrant;
import com.mcpscanner.auth.grant.RefreshGrant;
import com.mcpscanner.checks.api.CheckLog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RefreshGrantProbe {

    private static final String UNISSUED_PREFIX = "mcp-scanner-unissued-";

    private final RefreshExchanger exchanger;
    private final AuthorizationServerMetadata metadata;
    private final Function<String, JudgedToken> judge;
    private final ScopeSelection scopeSelection = new ScopeSelection();
    private final CheckLog log;

    RefreshGrantProbe(RefreshExchanger exchanger, AuthorizationServerMetadata metadata,
                      Function<String, JudgedToken> judge, CheckLog log) {
        this.exchanger = exchanger;
        this.metadata = metadata;
        this.judge = judge;
        this.log = log;
    }

    RefreshEvidence run(LiveRefreshGrant live) {
        RefreshGrant initial = live.grant();
        RefreshExchange control = refreshAndAdopt(live, ClientAuthentication.REGISTERED, Optional.empty());
        if (!control.issued()) {
            return RefreshEvidence.controlFailed(initial.client().type(), control);
        }
        RefreshExchange unissued = exchanger.exchange(live.grant(), UNISSUED_PREFIX + UUID.randomUUID(),
                ClientAuthentication.REGISTERED, Optional.empty());
        return new RefreshEvidence(initial.client().type(), control, Optional.of(unissued),
                unauthenticatedAttempts(live), scopeProbe(live, originalScope(initial, control)));
    }

    private List<JudgedRefresh> unauthenticatedAttempts(LiveRefreshGrant live) {
        if (!live.grant().client().type().isConfidential()) {
            return List.of();
        }
        return Stream.of(ClientAuthentication.OMITTED_SECRET, ClientAuthentication.WRONG_SECRET)
                .map(authentication -> refreshAndReauthenticate(live, authentication))
                .toList();
    }

    private JudgedRefresh refreshAndReauthenticate(LiveRefreshGrant live, ClientAuthentication authentication) {
        JudgedRefresh refresh = judged(refreshAndAdopt(live, authentication, Optional.empty()));
        if (refresh.issued()) {
            reestablish(live, Optional.empty(), "re-authenticate the live session after an unauthenticated refresh");
        }
        return refresh;
    }

    private Optional<ScopeProbe> scopeProbe(LiveRefreshGrant live, Optional<OriginalScope> original) {
        return original.flatMap(scope -> scopeSelection.extraScope(metadata.scopesSupported(), scope.scopes())
                .map(extra -> expand(live, scope, extra)));
    }

    private ScopeProbe expand(LiveRefreshGrant live, OriginalScope original, String extra) {
        Set<String> requested = Stream.concat(original.scopes().stream(), Stream.of(extra))
                .collect(Collectors.toUnmodifiableSet());
        JudgedRefresh refresh = judged(refreshAndAdopt(live, ClientAuthentication.REGISTERED, Optional.of(requested)));
        boolean needsRestore = refresh.issued() && !ScopeProbe.provenWithin(refresh.exchange(), original);
        boolean restored = needsRestore
                && reestablish(live, Optional.of(original.scopes()), "narrow the live session back to its original scope");
        return new ScopeProbe(extra, original, refresh, restored);
    }

    private boolean reestablish(LiveRefreshGrant live, Optional<Set<String>> scope, String purpose) {
        RefreshExchange reestablished = refreshAndAdopt(live, ClientAuthentication.REGISTERED, scope);
        if (!reestablished.issued()) {
            log.warn("could not " + purpose + " (HTTP " + reestablished.status()
                    + "); the session keeps the probe's token");
        }
        return reestablished.issued();
    }

    private JudgedRefresh judged(RefreshExchange exchange) {
        return new JudgedRefresh(exchange, exchange.tokens().map(tokens -> judge.apply(tokens.accessToken())));
    }

    private RefreshExchange refreshAndAdopt(LiveRefreshGrant live, ClientAuthentication authentication,
                                            Optional<Set<String>> scope) {
        RefreshExchange exchange = exchanger.exchange(live.grant(), live.grant().refreshToken(), authentication, scope);
        exchange.tokens().ifPresent(live::adopt);
        return exchange;
    }

    private static Optional<OriginalScope> originalScope(RefreshGrant initial, RefreshExchange control) {
        if (control.grantedScope().isPresent()) {
            return Optional.of(new OriginalScope(control.grantedScope().get(), true));
        }
        return initial.requestedScopes().isEmpty()
                ? Optional.empty()
                : Optional.of(new OriginalScope(Set.copyOf(initial.requestedScopes()), false));
    }
}
