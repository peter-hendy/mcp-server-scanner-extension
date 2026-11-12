package com.mcpscanner.identity.credentials;

import burp.api.montoya.logging.Logging;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.identity.credentials.SignInStatus.State;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

@Timeout(30)
class SignInQueueTest {

    private static final OAuthClientHints SHARED_OAUTH = new OAuthClientHints(URI.create("https://as.example"),
            List.of(), "client", null, false, 0, Duration.ofSeconds(30));

    private final List<String> started = new CopyOnWriteArrayList<>();
    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> interrupted = new CopyOnWriteArrayList<>();
    private final McpEventLog eventLog = new McpEventLog(mock(Logging.class));
    private IdentityRegistry registry;
    private SignInQueue queue;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        IdentityCredentials credentials = new IdentityCredentials(registry, () -> Optional.of(SHARED_OAUTH));
        queue = new SignInQueue(registry, credentials, this::fakeSignIn, worker, eventLog);
    }

    @AfterEach
    void tearDown() {
        gates.values().forEach(CountDownLatch::countDown);
        queue.close();
        worker.shutdownNow();
    }

    @Test
    void signsInTheConnectAsUserLikeAnyOtherUser() {
        Identity userOne = registry.primaryUser().withAuthKind(IdentityAuthKind.OAUTH);
        registry.update(userOne);

        queue.signIn(userOne.id());

        await().until(() -> registry.credential(userOne.id()).isPresent());
        assertThat(queue.status(userOne.id())).isEqualTo(SignInStatus.ready());
    }

    @Test
    void signsInEveryWaitingIdentityOneAtATimeInOrder() {
        Identity bob = oauthIdentity("bob");
        Identity carol = oauthIdentity("carol");
        Identity dave = oauthIdentity("dave");

        queue.signInAll();

        await().until(() -> registry.compared().size() == 3);
        assertThat(started).containsExactly(bob.id(), carol.id(), dave.id());
        assertThat(maxConcurrent).hasValue(1);
        assertThat(queue.status(carol.id())).isEqualTo(SignInStatus.ready());
    }

    @Test
    void reportsSigningInAndQueuedUntilTheBrowserSignInCompletes() {
        Identity bob = holdingSignIn(oauthIdentity("bob"));
        Identity carol = oauthIdentity("carol");

        queue.signInAll();

        await().until(() -> queue.status(bob.id()).state() == State.SIGNING_IN);
        assertThat(queue.status(carol.id()).state()).isEqualTo(State.QUEUED);
        gates.get(bob.id()).countDown();
        await().until(() -> queue.status(carol.id()).state() == State.READY);
        assertThat(queue.status(bob.id()).state()).isEqualTo(State.READY);
    }

    @Test
    void cancellingAQueuedSignInSkipsIt() {
        Identity bob = holdingSignIn(oauthIdentity("bob"));
        Identity carol = oauthIdentity("carol");
        Identity dave = oauthIdentity("dave");
        queue.signInAll();
        await().until(() -> queue.status(bob.id()).state() == State.SIGNING_IN);

        queue.cancel(carol.id());
        gates.get(bob.id()).countDown();

        await().until(() -> queue.status(dave.id()).state() == State.READY);
        assertThat(started).containsExactly(bob.id(), dave.id());
        assertThat(queue.status(carol.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
    }

    @Test
    void cancellingTheRunningSignInInterruptsItAndMovesOn() {
        Identity bob = holdingSignIn(oauthIdentity("bob"));
        Identity carol = oauthIdentity("carol");
        queue.signInAll();
        await().until(() -> queue.status(bob.id()).state() == State.SIGNING_IN);

        queue.cancel(bob.id());

        await().until(() -> queue.status(carol.id()).state() == State.READY);
        assertThat(queue.status(bob.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
        assertThat(registry.credential(bob.id())).isEmpty();
    }

    @Test
    void cancelAllStopsEverySignIn() {
        Identity bob = holdingSignIn(oauthIdentity("bob"));
        Identity carol = oauthIdentity("carol");
        queue.signInAll();
        await().until(() -> queue.status(bob.id()).state() == State.SIGNING_IN);

        queue.cancelAll();

        assertThat(queue.status(bob.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
        assertThat(queue.status(carol.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
        await().during(Duration.ofMillis(200)).until(() -> started.size() == 1);
    }

    @Test
    void aFailedSignInIsReportedWithItsReasonAndTheQueueContinues() {
        Identity bob = oauthIdentity("bob");
        Identity carol = oauthIdentity("carol");
        failures.put(bob.id(), new OAuthException("access_denied: user cancelled"));

        queue.signInAll();

        await().until(() -> queue.status(carol.id()).state() == State.READY);
        assertThat(queue.status(bob.id())).satisfies(status -> {
            assertThat(status.state()).isEqualTo(State.FAILED);
            assertThat(status.reason()).contains("access_denied: user cancelled");
        });
    }

    @Test
    void aSignInThatSucceedsElsewhereReplacesTheEarlierFailure() {
        Identity bob = oauthIdentity("bob");
        failures.put(bob.id(), new OAuthException("OAuth issuer URI is required"));
        List<String> changed = new CopyOnWriteArrayList<>();
        queue.signIn(bob.id());
        await().until(() -> queue.status(bob.id()).state() == State.FAILED);
        queue.addListener(changed::add);

        registry.setCredential(bob.id(), new NoAuthStrategy());
        queue.signedInElsewhere(bob.id());

        assertThat(queue.status(bob.id()).state()).isEqualTo(State.READY);
        assertThat(changed).containsExactly(bob.id());
    }

    @Test
    void signInAllSkipsIdentitiesThatAreReadyOrDoNotUseABrowser() {
        Identity bob = oauthIdentity("bob");
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("existing"));
        Identity carol = registry.add(Identity.create("carol", IdentityRole.PEER, IdentityAuthKind.BEARER));
        Identity dave = oauthIdentity("dave");

        queue.signInAll();

        await().until(() -> queue.status(dave.id()).state() == State.READY);
        assertThat(started).containsExactly(dave.id());
        assertThat(queue.status(carol.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
    }

    @Test
    void rejectsSignInForAnIdentityThatDoesNotUseABrowser() {
        Identity carol = registry.add(Identity.create("carol", IdentityRole.PEER, IdentityAuthKind.BEARER));

        assertThatThrownBy(() -> queue.signIn(carol.id())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notifiesListenersOfEachStatusChange() {
        Identity bob = oauthIdentity("bob");
        List<String> changed = new CopyOnWriteArrayList<>();
        queue.addListener(changed::add);

        queue.signIn(bob.id());

        await().until(() -> queue.status(bob.id()).state() == State.READY);
        await().until(() -> changed.size() >= 3);
        assertThat(changed).containsOnly(bob.id());
    }

    @Test
    void refusesSignInAfterClose() {
        Identity bob = oauthIdentity("bob");
        queue.close();

        assertThatThrownBy(() -> queue.signIn(bob.id())).isInstanceOf(IllegalStateException.class);
        assertThat(queue.status(bob.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
    }

    @Test
    void aRejectedSubmissionLeavesNothingQueuedToCancel() {
        Identity bob = oauthIdentity("bob");
        worker.shutdownNow();

        assertThatThrownBy(() -> queue.signIn(bob.id())).isInstanceOf(IllegalStateException.class);
        assertThat(queue.status(bob.id()).state()).isEqualTo(State.NEEDS_CREDENTIALS);
        queue.cancel(bob.id());
    }

    @Test
    void removingAnIdentityCancelsItsSignIn() {
        Identity bob = holdingSignIn(oauthIdentity("bob"));
        queue.signIn(bob.id());
        await().until(() -> queue.status(bob.id()).state() == State.SIGNING_IN);

        registry.remove(bob.id());

        await().until(() -> interrupted.contains(bob.id()));
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .contains("Sign-in cancelled for identity \"bob\"");
    }

    private AuthStrategy fakeSignIn(Identity identity, OAuthClientHints hints) {
        int running = concurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(running, Math::max);
        started.add(identity.id());
        try {
            awaitGate(identity.id());
            RuntimeException failure = failures.get(identity.id());
            if (failure != null) {
                throw failure;
            }
            return new BearerTokenAuthStrategy("token-" + identity.name());
        } finally {
            concurrent.decrementAndGet();
        }
    }

    private void awaitGate(String identityId) {
        CountDownLatch gate = gates.get(identityId);
        if (gate == null) {
            return;
        }
        try {
            gate.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException interruption) {
            interrupted.add(identityId);
            Thread.currentThread().interrupt();
            throw new OAuthException("Interrupted while waiting for OAuth callback", interruption);
        }
    }

    private Identity holdingSignIn(Identity identity) {
        gates.put(identity.id(), new CountDownLatch(1));
        return identity;
    }

    private Identity oauthIdentity(String name) {
        return registry.add(Identity.create(name, IdentityRole.PEER, IdentityAuthKind.OAUTH));
    }
}
