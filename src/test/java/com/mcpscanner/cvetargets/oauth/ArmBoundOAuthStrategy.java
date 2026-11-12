package com.mcpscanner.cvetargets.oauth;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.grant.RefreshGrantStore;
import com.mcpscanner.cvetargets.manifest.AuthorizationCodeGrant;
import com.mcpscanner.cvetargets.run.ArmBoundAuth;

import java.net.URI;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

public final class ArmBoundOAuthStrategy implements AuthStrategy, ArmBoundAuth {

    private static final Duration RETRY_PAUSE = Duration.ofSeconds(1);

    private final AuthorizationCodeLogin login;
    private volatile OAuthAuthCodeStrategy session;

    public ArmBoundOAuthStrategy(AuthorizationCodeGrant grant) {
        this.login = new AuthorizationCodeLogin(grant);
    }

    @Override
    public void bind(URI armEndpoint, Duration timeout) {
        AtomicReference<RuntimeException> lastFailure = new AtomicReference<>();
        try {
            await().atMost(timeout).pollDelay(Duration.ZERO).pollInterval(RETRY_PAUSE).pollInSameThread()
                    .until(() -> loggedIn(armEndpoint, lastFailure));
        } catch (ConditionTimeoutException timedOut) {
            throw new IllegalStateException("OAuth login to " + armEndpoint + " did not succeed within " + timeout,
                    lastFailure.get());
        }
    }

    private boolean loggedIn(URI armEndpoint, AtomicReference<RuntimeException> lastFailure) {
        try {
            session = login.login(armEndpoint);
            return true;
        } catch (RuntimeException failure) {
            lastFailure.set(failure);
            return false;
        }
    }

    @Override
    public Map<String, String> headers() {
        return bound().headers();
    }

    @Override
    public Set<String> contributedHeaderNames() {
        return bound().contributedHeaderNames();
    }

    @Override
    public boolean supportsRefresh() {
        return bound().supportsRefresh();
    }

    @Override
    public boolean refresh() {
        return bound().refresh();
    }

    @Override
    public boolean isTerminallyFailed() {
        return bound().isTerminallyFailed();
    }

    @Override
    public Optional<RefreshGrantStore> refreshGrantStore() {
        return bound().refreshGrantStore();
    }

    private OAuthAuthCodeStrategy bound() {
        if (session == null) {
            throw new IllegalStateException("the OAuth session is obtained when the arm starts");
        }
        return session;
    }
}
