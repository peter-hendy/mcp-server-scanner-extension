package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.FailureReason;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.credentials.SignInStatus.State;
import com.mcpscanner.logging.McpEventLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class SignInQueue implements AutoCloseable {

    private final IdentityRegistry registry;
    private final IdentityCredentials credentials;
    private final IdentitySignIn signIn;
    private final ExecutorService worker;
    private final McpEventLog eventLog;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Map<String, String> failures = new HashMap<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    public SignInQueue(IdentityRegistry registry, IdentityCredentials credentials, IdentitySignIn signIn,
                       ExecutorService worker, McpEventLog eventLog) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.signIn = Objects.requireNonNull(signIn, "signIn");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        registry.addListener(this::cancelRemovedIdentities);
    }

    public void signIn(String identityId) {
        if (!(credentials.requirement(identityId) instanceof CredentialRequirement.BrowserSignInRequired required)) {
            throw new IllegalArgumentException("This identity does not sign in through a browser");
        }
        if (enqueue(registry.require(identityId), required.hints())) {
            fireChanged(identityId);
        }
    }

    public void signInAll() {
        registry.identities().stream()
                .filter(identity -> registry.credential(identity.id()).isEmpty())
                .filter(identity -> credentials.requirement(identity.id())
                        instanceof CredentialRequirement.BrowserSignInRequired)
                .forEach(identity -> signIn(identity.id()));
    }

    public void cancel(String identityId) {
        Attempt cancelled;
        synchronized (this) {
            cancelled = attempts.remove(identityId);
        }
        if (cancelled != null) {
            stop(List.of(cancelled));
        }
    }

    private void cancelRemovedIdentities() {
        List<Attempt> orphaned;
        synchronized (this) {
            orphaned = attempts.values().stream()
                    .filter(attempt -> registry.find(attempt.identity().id()).isEmpty())
                    .toList();
            orphaned.forEach(attempt -> attempts.remove(attempt.identity().id()));
        }
        stop(orphaned);
    }

    public void cancelAll() {
        List<Attempt> cancelled;
        synchronized (this) {
            cancelled = List.copyOf(attempts.values());
            attempts.clear();
        }
        stop(cancelled);
    }

    private void stop(List<Attempt> cancelled) {
        cancelled.forEach(attempt -> attempt.future().cancel(true));
        cancelled.forEach(attempt -> {
            eventLog.info("Sign-in cancelled for identity \"" + attempt.identity().name() + "\"");
            fireChanged(attempt.identity().id());
        });
    }

    public synchronized SignInStatus status(String identityId) {
        Attempt attempt = attempts.get(identityId);
        if (attempt != null) {
            return SignInStatus.of(attempt.state());
        }
        if (failures.containsKey(identityId)) {
            return SignInStatus.failed(failures.get(identityId));
        }
        return registry.credential(identityId).isPresent()
                ? SignInStatus.ready() : SignInStatus.of(State.NEEDS_CREDENTIALS);
    }

    public void signedInElsewhere(String identityId) {
        boolean hadFailed;
        synchronized (this) {
            hadFailed = failures.remove(identityId) != null;
        }
        if (hadFailed) {
            fireChanged(identityId);
        }
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        cancelAll();
    }

    private synchronized boolean enqueue(Identity identity, OAuthClientHints hints) {
        if (closed) {
            throw new IllegalStateException("Identity sign-in has stopped");
        }
        if (attempts.containsKey(identity.id())) {
            return false;
        }
        Attempt attempt = new Attempt(identity);
        attempts.put(identity.id(), attempt);
        try {
            attempt.start(worker.submit(() -> run(attempt, hints)));
        } catch (RejectedExecutionException rejected) {
            attempts.remove(identity.id());
            throw new IllegalStateException("Identity sign-in has stopped", rejected);
        }
        failures.remove(identity.id());
        return true;
    }

    private void run(Attempt attempt, OAuthClientHints hints) {
        if (!advance(attempt)) {
            return;
        }
        try {
            complete(attempt, signIn.signIn(attempt.identity(), hints));
        } catch (RuntimeException failure) {
            fail(attempt, failure);
        }
    }

    private boolean advance(Attempt attempt) {
        synchronized (this) {
            if (attempts.get(attempt.identity().id()) != attempt) {
                return false;
            }
            attempt.markSigningIn();
        }
        fireChanged(attempt.identity().id());
        return true;
    }

    private void complete(Attempt attempt, AuthStrategy credential) {
        if (finish(attempt)) {
            registry.setCredential(attempt.identity().id(), credential);
            eventLog.info("Identity \"" + attempt.identity().name() + "\" signed in");
            fireChanged(attempt.identity().id());
        }
    }

    private void fail(Attempt attempt, RuntimeException failure) {
        String reason = FailureReason.describe(failure);
        synchronized (this) {
            if (!finish(attempt)) {
                return;
            }
            failures.put(attempt.identity().id(), reason);
        }
        eventLog.warn("Sign-in failed for identity \"" + attempt.identity().name() + "\": " + reason);
        fireChanged(attempt.identity().id());
    }

    private synchronized boolean finish(Attempt attempt) {
        return attempts.remove(attempt.identity().id(), attempt);
    }

    private void fireChanged(String identityId) {
        listeners.forEach(listener -> listener.accept(identityId));
    }

    private static final class Attempt {
        private final Identity identity;
        private volatile State state = State.QUEUED;
        private volatile Future<?> future;

        Attempt(Identity identity) {
            this.identity = identity;
        }

        Identity identity() {
            return identity;
        }

        State state() {
            return state;
        }

        Future<?> future() {
            return future;
        }

        void start(Future<?> submitted) {
            future = submitted;
        }

        void markSigningIn() {
            state = State.SIGNING_IN;
        }
    }
}
