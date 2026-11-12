package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.oauth.AuthorizationCallback;
import com.mcpscanner.core.oauth.AuthorizationCodeConfig;
import com.mcpscanner.core.oauth.BrowserCallback;
import com.mcpscanner.core.oauth.RegisteredClient;
import com.mcpscanner.core.oauth.TokenRequest;
import com.mcpscanner.core.oauth.TokenResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.testutil.Polling.within;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TokenRefreshDoesNotBlockReadersTest {

    private static final URI ISSUER = URI.create("https://issuer.example");
    private static final URI RESOURCE = URI.create("https://mcp.example/mcp");
    private static final URI CALLBACK = URI.create("http://127.0.0.1:49152/callback");

    private final AtomicBoolean refreshStarted = new AtomicBoolean();
    private final CountDownLatch releaseRefresh = new CountDownLatch(1);
    private final AtomicInteger exchanges = new AtomicInteger();

    @AfterEach
    void releaseBlockedRefresh() {
        releaseRefresh.countDown();
    }

    @Test
    void sessionTokensStayReadableWhileARefreshIsInFlight() throws Exception {
        SharedOAuthSession session = authenticatedSession();
        CompletableFuture<OAuthTokens> refresh = CompletableFuture.supplyAsync(session::refresh);
        within(Duration.ofSeconds(5)).untilTrue(refreshStarted);

        OAuthTokens current = valueWithin(Duration.ofSeconds(1), CompletableFuture.supplyAsync(session::currentTokens));

        assertThat(current.accessToken().getValue()).isEqualTo("access-1");
        releaseRefresh.countDown();
        assertThat(valueWithin(Duration.ofSeconds(5), refresh).accessToken().getValue()).isEqualTo("access-2");
    }

    @Test
    void strategyTokenDisplayStaysReadableWhileARefreshIsInFlight() throws Exception {
        OAuthAuthCodeStrategy strategy = new OAuthAuthCodeStrategy(ISSUER, "client", authenticatedSession(),
                Clock.systemUTC(), McpEventLog.noop());
        CompletableFuture<Boolean> refresh = CompletableFuture.supplyAsync(strategy::refresh);
        within(Duration.ofSeconds(5)).untilTrue(refreshStarted);

        var token = valueWithin(Duration.ofSeconds(1), CompletableFuture.supplyAsync(strategy::currentToken));
        var state = valueWithin(Duration.ofSeconds(1), CompletableFuture.supplyAsync(strategy::snapshot));

        assertThat(token).hasValueSatisfying(value -> assertThat(value.value()).isEqualTo("access-1"));
        assertThat(state.valid()).isTrue();
        releaseRefresh.countDown();
        assertThat(valueWithin(Duration.ofSeconds(5), refresh)).isTrue();
    }

    @Test
    void concurrentForcedRefreshesAreSerialisedRatherThanInterleaved() throws Exception {
        OAuthAuthCodeStrategy strategy = new OAuthAuthCodeStrategy(ISSUER, "client", authenticatedSession(),
                Clock.systemUTC(), McpEventLog.noop());
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(strategy::refresh);
        within(Duration.ofSeconds(5)).untilTrue(refreshStarted);
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(strategy::refresh);

        assertThat(exchanges.get()).isEqualTo(2);
        releaseRefresh.countDown();

        assertThat(valueWithin(Duration.ofSeconds(5), first)).isTrue();
        assertThat(valueWithin(Duration.ofSeconds(5), second)).isTrue();
        assertThat(exchanges.get()).isEqualTo(3);
    }

    private SharedOAuthSession authenticatedSession() {
        var config = new AuthorizationCodeConfig(ISSUER, RESOURCE, ISSUER.resolve("/authorize"),
                ISSUER.resolve("/token"), new RegisteredClient(ISSUER, "client", null), CALLBACK, Set.of("read"), true);
        var session = new SharedOAuthSession(config, this::exchange,
                request -> new BrowserCallback(CALLBACK, new AuthorizationCallback(
                        request.parameters().get("state"), ISSUER.toString(), "code", null, null)),
                Clock.systemUTC());
        session.authenticate();
        return session;
    }

    private TokenResponse exchange(TokenRequest request) {
        int count = exchanges.incrementAndGet();
        if (count > 1) {
            refreshStarted.set(true);
            awaitRelease();
        }
        return new TokenResponse(200, Map.of("access_token", "access-" + count, "refresh_token", "refresh-" + count,
                "token_type", "Bearer", "expires_in", 3600, "scope", "read"));
    }

    private void awaitRelease() {
        try {
            if (!releaseRefresh.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("refresh was never released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
