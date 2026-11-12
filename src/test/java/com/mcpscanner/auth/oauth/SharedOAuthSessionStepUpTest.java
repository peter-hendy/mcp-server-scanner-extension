package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.RecoveryResult;
import com.mcpscanner.core.oauth.*;
import com.mcpscanner.core.protocol.McpFlow;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.testutil.Polling.within;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SharedOAuthSessionStepUpTest {
    private final URI issuer = URI.create("https://issuer.example");
    private final URI resource = URI.create("https://mcp.example/mcp");
    private final URI callback = URI.create("http://127.0.0.1:8123/callback");
    private final List<AuthorizationRequest> authorizations = new ArrayList<>();
    private final List<TokenRequest> exchanges = new ArrayList<>();

    @Test
    void strategyRefreshDelegatesToSharedSessionAndPublishesRotatedCredential() {
        SharedOAuthSession session = session();
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED);
        var provider = strategy.sharedCredentialProvider().orElseThrow();

        assertThat(strategy.refresh()).isTrue();

        assertThat(exchanges).hasSize(2);
        assertThat(exchanges.get(1).form()).containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "refresh-1")
                .containsEntry("resource", resource.toString());
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(provider.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(strategy.refreshToken().getValue()).isEqualTo("refresh-2");
    }

    @Test
    void strategyKeepsSharedRefreshTokenWhenRefreshResponseOmitsRotation() {
        SharedOAuthSession session = session(Clock.systemUTC(), request -> {
            if (exchanges.size() == 2) {
                return new TokenResponse(200, Map.of("access_token", "access-2",
                        "token_type", "Bearer", "expires_in", 3600, "scope", "read profile"));
            }
            return tokenResponse(exchanges.size() - 1);
        });
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED);

        assertThat(strategy.refresh()).isTrue();
        assertThat(strategy.refreshToken().getValue()).isEqualTo("refresh-1");
        assertThat(strategy.refresh()).isTrue();

        assertThat(exchanges.get(1).form()).containsEntry("refresh_token", "refresh-1");
        assertThat(exchanges.get(2).form()).containsEntry("refresh_token", "refresh-1");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-3");
    }

    @Test
    void renewUsesTheRefreshTokenWhenTheSessionHasOne() {
        OAuthAuthCodeStrategy strategy = strategy(session(), ignored -> AuthorizationApproval.DENIED);

        assertThat(strategy.renew()).isTrue();

        assertThat(authorizations).hasSize(1);
        assertThat(exchanges.get(1).form()).containsEntry("grant_type", "refresh_token");
        assertThat(strategy.currentToken()).hasValueSatisfying(token -> {
            assertThat(token.value()).isEqualTo("access-2");
            assertThat(token.expiresAt()).isPresent();
        });
    }

    @Test
    void renewReauthorizesInTheBrowserWhenThereIsNoRefreshToken() {
        SharedOAuthSession session = session(Clock.systemUTC(), request ->
                new TokenResponse(200, Map.of("access_token", "access-" + exchanges.size(),
                        "token_type", "Bearer", "expires_in", 3600, "scope", "read")));
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED);

        assertThat(strategy.renew()).isTrue();

        assertThat(authorizations).hasSize(2);
        assertThat(exchanges.get(1).form()).containsEntry("grant_type", "authorization_code");
        assertThat(strategy.currentToken().orElseThrow().value()).isEqualTo("access-2");
    }

    @Test
    void strategyRejectsRefreshWhenAuthorizationReturnedNoRefreshToken() {
        SharedOAuthSession session = session(Clock.systemUTC(), request ->
                new TokenResponse(200, Map.of("access_token", "access-1",
                        "token_type", "Bearer", "expires_in", 3600, "scope", "read")));
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED);

        assertThat(strategy.refreshToken()).isNull();
        assertThat(strategy.refresh()).isFalse();
        assertThat(exchanges).hasSize(1);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");
    }

    @Test
    void strategyTripsTerminalCircuitBreakerAndNotifiesListenerOnceAfterSharedRefreshFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SharedOAuthSession session = session(clock, request -> {
            if (exchanges.size() > 1) {
                return new TokenResponse(400, Map.of("error", "invalid_grant",
                        "error_description", "secret-marker"));
            }
            return tokenResponse(exchanges.size() - 1);
        });
        McpEventLog log = new McpEventLog(null);
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED, clock, log);
        AtomicInteger listenerCalls = new AtomicInteger();
        strategy.setTerminalFailureListener(listenerCalls::incrementAndGet);
        clock.advance(Duration.ofSeconds(3570));

        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");

        assertThat(strategy.isTerminallyFailed()).isTrue();
        assertThat(listenerCalls).hasValue(1);
        assertThat(exchanges).hasSize(4);
        assertThat(log.snapshot()).noneMatch(entry -> entry.message().contains("secret-marker"));
    }

    @Test
    void successfulSharedRefreshResetsConsecutiveFailureCount() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SharedOAuthSession session = session(clock, request -> {
            int requestCount = exchanges.size();
            if (requestCount == 2 || requestCount == 3 || requestCount >= 5) {
                return new TokenResponse(400, Map.of("error", "invalid_grant"));
            }
            return tokenResponse(requestCount - 1);
        });
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED, clock,
                McpEventLog.noop());

        assertThat(strategy.refresh()).isFalse();
        assertThat(strategy.refresh()).isFalse();
        assertThat(strategy.refresh()).isTrue();
        assertThat(strategy.refresh()).isFalse();
        assertThat(strategy.refresh()).isFalse();
        assertThat(strategy.isTerminallyFailed()).isFalse();
        assertThat(strategy.refresh()).isFalse();

        assertThat(strategy.isTerminallyFailed()).isTrue();
        assertThat(exchanges).hasSize(7);
    }

    @Test
    void concurrentExpiredHeadersShareOneRefreshAndSubsequentHeadersStayDebounced() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SharedOAuthSession session = session(clock, request -> tokenResponse(exchanges.size() - 1));
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED, clock,
                McpEventLog.noop());
        clock.advance(Duration.ofSeconds(3570));

        int threadCount = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Map<String, String>>> headers = new ArrayList<>();
            for (int index = 0; index < threadCount; index++) {
                headers.add(pool.submit(() -> {
                    start.await();
                    return strategy.headers();
                }));
            }
            start.countDown();
            for (Future<Map<String, String>> header : headers) {
                within(Duration.ofSeconds(5)).until(header::isDone);
                assertThat(header.get())
                        .containsEntry("Authorization", "Bearer access-2");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(exchanges).hasSize(2);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(exchanges).hasSize(2);
    }

    @Test
    void snapshotTracksExpiryAndBecomesValidAfterSharedSessionRefresh() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SharedOAuthSession session = session(clock, request -> tokenResponse(exchanges.size() - 1));
        OAuthAuthCodeStrategy strategy = strategy(session, ignored -> AuthorizationApproval.DENIED, clock,
                McpEventLog.noop());

        assertThat(strategy.snapshot().valid()).isTrue();
        clock.advance(Duration.ofSeconds(3601));
        assertThat(strategy.snapshot().valid()).isFalse();

        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(strategy.snapshot().valid()).isTrue();
    }

    @Test
    void approvedStepUpUnionsGrantedScopesAndUpdatesStrategyAndRefreshBinding() {
        SharedOAuthSession session = session();
        List<Set<String>> prompts = new ArrayList<>();
        OAuthAuthCodeStrategy strategy = strategy(session, scopes -> {
            prompts.add(scopes);
            return AuthorizationApproval.APPROVED;
        });
        var provider = strategy.sharedCredentialProvider().orElseThrow();
        var recovery = provider.beginOperation("tools/list");

        assertThat(recovery.recover(challenge())).isEqualTo(RecoveryResult.RETRY);
        assertThat(prompts).containsExactly(Set.of("read", "profile", "write"));
        assertThat(authorizations).hasSize(2);
        assertThat(Set.of(authorizations.get(1).parameters().get("scope").split(" ")))
                .containsExactlyInAnyOrder("read", "profile", "write");
        assertThat(authorizations.get(1).endpoint()).isEqualTo(issuer.resolve("/authorize"));
        assertThat(authorizations.get(1).parameters()).containsEntry("resource", resource.toString())
                .containsEntry("client_id", "client").containsEntry("redirect_uri", callback.toString());
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(strategy.refreshToken().getValue()).isEqualTo("refresh-2");
        assertThat(strategy.requestedScopes()).containsExactlyInAnyOrder("read", "profile", "write");
        assertThat(strategy.refresh()).isTrue();
        assertThat(exchanges.get(2).form()).containsEntry("refresh_token", "refresh-2")
                .containsEntry("resource", resource.toString());
        assertThat(provider.headers()).containsEntry("Authorization", "Bearer access-3");
        assertThat(recovery.recover(challenge())).isEqualTo(RecoveryResult.STOP);
        assertThat(prompts).hasSize(1);
    }

    @Test
    void deniedIncreaseNeverOpensBrowserAndStaysDeniedForOperation() {
        SharedOAuthSession session = session();
        List<Set<String>> prompts = new ArrayList<>();
        var strategy = strategy(session, scopes -> {
            prompts.add(scopes);
            return AuthorizationApproval.DENIED;
        });
        var recovery = strategy.sharedCredentialProvider().orElseThrow().beginOperation("tools/list");
        assertThat(recovery.recover(challenge())).isEqualTo(RecoveryResult.DENIED);
        assertThat(recovery.recover(challenge())).isEqualTo(RecoveryResult.STOP);
        assertThat(prompts).hasSize(1);
        assertThat(authorizations).hasSize(1);
        assertThat(exchanges).hasSize(1);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-1");
    }

    @Test
    void delayedStepUpPublicationCannotReplaceNewerRefreshedCredential() throws Exception {
        var stepUpExchanged = new AtomicBoolean();
        var config = new AuthorizationCodeConfig(issuer, resource, issuer.resolve("/authorize"),
                issuer.resolve("/token"), new RegisteredClient(issuer, "client", null), callback, Set.of("read"), true);
        var session = new SharedOAuthSession(config, request -> {
            exchanges.add(request);
            int count = exchanges.size();
            if (count == 2) {
                stepUpExchanged.set(true);
            }
            return new TokenResponse(200, Map.of("access_token", "access-" + count,
                    "refresh_token", "refresh-" + count, "token_type", "Bearer", "expires_in", 3600,
                    "scope", count == 1 ? "read" : "read write"));
        }, request -> new BrowserCallback(callback, new AuthorizationCallback(request.parameters().get("state"),
                issuer.toString(), "code", null, null)), Clock.systemUTC());
        var strategy = strategy(session, ignored -> AuthorizationApproval.APPROVED);
        var provider = strategy.sharedCredentialProvider().orElseThrow();
        CompletableFuture<RecoveryResult> stepUp;
        synchronized (strategy) {
            stepUp = CompletableFuture.supplyAsync(() -> provider.beginOperation("tools/list").recover(challenge()));
            within(Duration.ofSeconds(5)).untilTrue(stepUpExchanged);
            assertThat(strategy.refresh()).isTrue();
            assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-3");
        }
        within(Duration.ofSeconds(5)).until(stepUp::isDone);
        assertThat(stepUp.join()).isEqualTo(RecoveryResult.RETRY);
        assertThat(provider.headers()).containsEntry("Authorization", "Bearer access-3");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-3");
        assertThat(strategy.refreshToken().getValue()).isEqualTo("refresh-3");
    }

    @Test
    void wrongIssuerDuringStepUpDoesNotExchangeCodeOrReplaceOriginalCredential() {
        SharedOAuthSession session = session(request -> authorizations.size() == 1 ? issuer.toString() : "https://wrong.example");
        var strategy = strategy(session, ignored -> AuthorizationApproval.APPROVED);
        var provider = strategy.sharedCredentialProvider().orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.beginOperation("tools/list").recover(challenge()))
                .isInstanceOf(OAuthException.class).hasMessageContaining("issuer mismatch");
        assertThat(exchanges).hasSize(1);
        assertThat(provider.headers()).containsEntry("Authorization", "Bearer access-1");
        assertThat(strategy.refresh()).isTrue();
        assertThat(exchanges.get(1).form()).containsEntry("refresh_token", "refresh-1");
    }

    @Test
    void unavailableBrowserOrCallbackProducesActionableFailureWithoutReplacingCredential() {
        SharedOAuthSession session = session(request -> {
            if (authorizations.size() > 1) {
                throw new IllegalStateException("untrusted diagnostic containing a secret");
            }
            return issuer.toString();
        });
        var strategy = strategy(session, ignored -> AuthorizationApproval.APPROVED);
        var provider = strategy.sharedCredentialProvider().orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.beginOperation("tools/list").recover(challenge()))
                .isInstanceOf(OAuthException.class).hasMessageContaining("reconnect")
                .hasMessageNotContaining("secret");
        assertThat(exchanges).hasSize(1);
        assertThat(provider.headers()).containsEntry("Authorization", "Bearer access-1");
    }

    private OAuthAuthCodeStrategy strategy(SharedOAuthSession session,
            java.util.function.Function<Set<String>, AuthorizationApproval> approval) {
        session.authenticate();
        return new OAuthAuthCodeStrategy(issuer, "client",
                session, Clock.systemUTC(), McpEventLog.noop(), approval);
    }

    private OAuthAuthCodeStrategy strategy(SharedOAuthSession session,
            java.util.function.Function<Set<String>, AuthorizationApproval> approval,
            Clock clock, McpEventLog eventLog) {
        session.authenticate();
        return new OAuthAuthCodeStrategy(issuer, "client",
                session, clock, eventLog, approval);
    }

    private SharedOAuthSession session() {
        return session(ignored -> issuer.toString());
    }

    private SharedOAuthSession session(java.util.function.Function<AuthorizationRequest, String> callbackIssuer) {
        return session(Clock.systemUTC(), request -> {
            int count = exchanges.size() - 1;
            return tokenResponse(count);
        }, callbackIssuer);
    }

    private SharedOAuthSession session(Clock clock,
            java.util.function.Function<TokenRequest, TokenResponse> tokenResponse) {
        return session(clock, tokenResponse, ignored -> issuer.toString());
    }

    private SharedOAuthSession session(Clock clock,
            java.util.function.Function<TokenRequest, TokenResponse> tokenResponse,
            java.util.function.Function<AuthorizationRequest, String> callbackIssuer) {
        var config = new AuthorizationCodeConfig(issuer, resource, issuer.resolve("/authorize"),
                issuer.resolve("/token"), new RegisteredClient(issuer, "client", null), callback, Set.of("read"), true);
        return new SharedOAuthSession(config, request -> {
            exchanges.add(request);
            return tokenResponse.apply(request);
        }, request -> {
            authorizations.add(request);
            return new BrowserCallback(callback, new AuthorizationCallback(request.parameters().get("state"),
                    callbackIssuer.apply(request), "code", null, null));
        }, clock);
    }

    private TokenResponse tokenResponse(int count) {
        return new TokenResponse(200, Map.of("access_token", "access-" + (count + 1),
                "refresh_token", "refresh-" + (count + 1), "token_type", "Bearer", "expires_in", 3600,
                "scope", count == 0 ? "read profile" : "read profile write"));
    }

    private McpFlow.HttpResponse challenge() {
        return new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate",
                "Bearer error=\"insufficient_scope\", scope=\"write\""), "", false);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
