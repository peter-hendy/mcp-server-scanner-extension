package com.mcpscanner.auth;

import com.mcpscanner.auth.grant.ClientType;
import com.mcpscanner.auth.grant.IssuedTokens;
import com.mcpscanner.auth.grant.RefreshGrant;
import com.mcpscanner.auth.grant.RefreshGrantStore;
import com.mcpscanner.auth.oauth.TestOAuthStrategies;
import com.mcpscanner.auth.oauth.TestOAuthStrategies.Client;
import com.mcpscanner.auth.oauth.TestOAuthStrategies.Login;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.oauth.RegistrationMode;
import com.mcpscanner.core.oauth.TokenExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.testutil.Polling.within;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OAuthAuthCodeStrategyGrantStoreTest {

    private static final URI ISSUER = URI.create("https://issuer.example.com");
    private static final URI RESOURCE = URI.create("https://mcp.example.com/mcp");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void nonOAuthStrategiesOfferNoGrantStore() {
        assertThat(new NoAuthStrategy().refreshGrantStore()).isEmpty();
        assertThat(new BearerTokenAuthStrategy("t").refreshGrantStore()).isEmpty();
    }

    @Test
    void exposesTheLiveGrantWithItsClientType() {
        OAuthAuthCodeStrategy strategy = strategy("client-secret", ClientType.CONFIDENTIAL_REGISTERED, "refresh-1");

        RefreshGrant grant = store(strategy).withExclusiveGrant(live -> live.grant()).orElseThrow();

        assertThat(grant.issuer()).isEqualTo(ISSUER);
        assertThat(grant.resource()).contains(RESOURCE);
        assertThat(grant.refreshToken()).isEqualTo("refresh-1");
        assertThat(grant.requestedScopes()).containsExactly("mcp:tools");
        assertThat(grant.client().clientId()).isEqualTo("client-1");
        assertThat(grant.client().secret()).contains("client-secret");
        assertThat(grant.client().type()).isEqualTo(ClientType.CONFIDENTIAL_REGISTERED);
    }

    @Test
    void aPreRegisteredClientDerivesItsTypeFromTheConfiguredSecret() {
        OAuthAuthCodeStrategy confidential = preRegisteredStrategy("client-secret");
        OAuthAuthCodeStrategy publicClient = preRegisteredStrategy(null);

        assertThat(clientType(confidential)).isEqualTo(ClientType.CONFIDENTIAL_CONFIGURED);
        assertThat(clientType(publicClient)).isEqualTo(ClientType.PUBLIC);
    }

    @Test
    void adoptedTokensBecomeTheLiveSessionTokens() {
        OAuthAuthCodeStrategy strategy = strategy(null, ClientType.PUBLIC, "refresh-1");

        store(strategy).withExclusiveGrant(live -> {
            live.adopt(new IssuedTokens("access-2", Optional.of("refresh-2"), Duration.ofMinutes(10)));
            return live.grant();
        });

        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
        assertThat(strategy.refreshToken().getValue()).isEqualTo("refresh-2");
        assertThat(strategy.snapshot().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void adoptingTokensWithoutARefreshTokenKeepsTheCurrentRefreshToken() {
        OAuthAuthCodeStrategy strategy = strategy(null, ClientType.PUBLIC, "refresh-1");

        RefreshGrant afterAdopt = store(strategy).withExclusiveGrant(live -> {
            live.adopt(new IssuedTokens("access-2", Optional.empty(), Duration.ofMinutes(10)));
            return live.grant();
        }).orElseThrow();

        assertThat(afterAdopt.refreshToken()).isEqualTo("refresh-1");
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer access-2");
    }

    @Test
    void theGrantSeenAfterAnAdoptionCarriesTheRotatedRefreshToken() {
        OAuthAuthCodeStrategy strategy = strategy(null, ClientType.PUBLIC, "refresh-1");

        String rotated = store(strategy).withExclusiveGrant(live -> {
            live.adopt(new IssuedTokens("access-2", Optional.of("refresh-2"), Duration.ofMinutes(10)));
            return live.grant().refreshToken();
        }).orElseThrow();

        assertThat(rotated).isEqualTo("refresh-2");
    }

    @Test
    void offersNoGrantWithoutARefreshToken() {
        OAuthAuthCodeStrategy strategy = strategy(null, ClientType.PUBLIC, null);

        assertThat(store(strategy).withExclusiveGrant(live -> "ran")).isEmpty();
    }

    @Test
    void offersNoGrantOnceTheRefreshCircuitBreakerHasTripped() {
        OAuthAuthCodeStrategy strategy = TestOAuthStrategies.authenticated(
                login(publicClient(), "refresh-1", NOW.plusSeconds(1)), TestOAuthStrategies.refusingRefresh());
        strategy.refresh();
        strategy.refresh();
        strategy.refresh();

        assertThat(strategy.isTerminallyFailed()).isTrue();
        assertThat(store(strategy).withExclusiveGrant(live -> "ran")).isEmpty();
    }

    @Test
    void scannerTrafficKeepsReadingTheCurrentTokenWhileACheckHoldsTheGrant() throws Exception {
        OAuthAuthCodeStrategy strategy = strategy(null, ClientType.PUBLIC, "refresh-1");
        AtomicBoolean holding = new AtomicBoolean();
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Optional<String>> check = CompletableFuture.supplyAsync(() ->
                store(strategy).withExclusiveGrant(live -> {
                    holding.set(true);
                    awaitGate(release);
                    return "done";
                }));
        within(Duration.ofSeconds(5)).untilTrue(holding);
        String headerWhileHeld = valueWithin(Duration.ofSeconds(2),
                CompletableFuture.supplyAsync(() -> strategy.headers().get("Authorization")));
        release.countDown();

        assertThat(headerWhileHeld).isEqualTo("Bearer access-1");
        assertThat(valueWithin(Duration.ofSeconds(5), check)).contains("done");
    }


    @Test
    void aCheckWaitingOnAnotherLockNeverDeadlocksWithAThreadThatRefreshesUnderThatLock() throws Exception {
        OAuthAuthCodeStrategy strategy = expiringStrategy(new AtomicInteger());
        Object sessionLock = new Object();
        CountDownLatch checkHoldsGrant = new CountDownLatch(1);
        CountDownLatch sessionHeld = new CountDownLatch(1);

        CompletableFuture<Void> proxyThread = CompletableFuture.runAsync(() -> {
            awaitGate(checkHoldsGrant);
            synchronized (sessionLock) {
                sessionHeld.countDown();
                strategy.refresh();
                strategy.headers();
            }
        });
        CompletableFuture<Optional<String>> check = CompletableFuture.supplyAsync(() ->
                store(strategy).withExclusiveGrant(live -> {
                    checkHoldsGrant.countDown();
                    awaitGate(sessionHeld);
                    synchronized (sessionLock) {
                        return "sent";
                    }
                }));

        assertThat(valueWithin(Duration.ofSeconds(5), check)).contains("sent");
        valueWithin(Duration.ofSeconds(5), proxyThread);
    }

    @Test
    void aForcedRefreshOrRenewalDuringALeaseFailsFastAndLeavesTheGrantToTheCheck() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        OAuthAuthCodeStrategy strategy = expiringStrategy(refreshes);
        AtomicBoolean leaseHeld = new AtomicBoolean();
        CountDownLatch releaseLease = new CountDownLatch(1);
        CompletableFuture<Optional<String>> check = CompletableFuture.supplyAsync(() ->
                store(strategy).withExclusiveGrant(live -> {
                    leaseHeld.set(true);
                    awaitGate(releaseLease);
                    return "done";
                }));
        within(Duration.ofSeconds(5)).untilTrue(leaseHeld);

        boolean forced = valueWithin(Duration.ofSeconds(2), CompletableFuture.supplyAsync(strategy::refresh));
        boolean renewed = valueWithin(Duration.ofSeconds(2), CompletableFuture.supplyAsync(strategy::renew));
        int refreshesWhileHeld = refreshes.get();
        releaseLease.countDown();
        valueWithin(Duration.ofSeconds(5), check);

        assertThat(forced).isFalse();
        assertThat(renewed).isFalse();
        assertThat(refreshesWhileHeld).isZero();
        assertThat(strategy.refresh()).isTrue();
        assertThat(refreshes).hasValue(1);
    }

    private static OAuthAuthCodeStrategy expiringStrategy(AtomicInteger refreshes) {
        TokenExchange counting = request -> {
            refreshes.incrementAndGet();
            return TestOAuthStrategies.tokens("access-2", "refresh-2", Duration.ofMinutes(10));
        };
        return TestOAuthStrategies.authenticated(login(publicClient(), "refresh-1", NOW.plusSeconds(1)), counting);
    }

    private static void awaitGate(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static RefreshGrantStore store(AuthStrategy strategy) {
        return strategy.refreshGrantStore().orElseThrow();
    }

    private static ClientType clientType(OAuthAuthCodeStrategy strategy) {
        return store(strategy).withExclusiveGrant(live -> live.grant().client().type()).orElseThrow();
    }

    private static OAuthAuthCodeStrategy strategy(String secret, ClientType type, String refreshToken) {
        Client client = type == ClientType.CONFIDENTIAL_REGISTERED
                ? new Client("client-1", secret, "client_secret_basic", RegistrationMode.DCR)
                : new Client("client-1", secret, "none", RegistrationMode.DCR);
        return TestOAuthStrategies.authenticated(login(client, refreshToken, NOW.plusSeconds(600)),
                TestOAuthStrategies.refusingRefresh());
    }

    private static OAuthAuthCodeStrategy preRegisteredStrategy(String secret) {
        return TestOAuthStrategies.authenticated(
                login(new Client("client-1", secret, secret == null ? "none" : "client_secret_basic",
                        RegistrationMode.PRE_REGISTERED), "refresh-1",
                        NOW.plusSeconds(600)), TestOAuthStrategies.refusingRefresh());
    }

    private static Client publicClient() {
        return new Client("client-1", null, "none", RegistrationMode.DCR);
    }

    private static Login login(Client client, String refreshToken, Instant expiresAt) {
        return new Login(ISSUER, RESOURCE, List.of("mcp:tools"), client, "access-1", refreshToken, expiresAt, CLOCK);
    }
}
