package com.mcpscanner.auth;

import com.mcpscanner.auth.grant.ClientType;
import com.mcpscanner.auth.grant.IssuedTokens;
import com.mcpscanner.auth.grant.LiveRefreshGrant;
import com.mcpscanner.auth.grant.OAuthClient;
import com.mcpscanner.auth.grant.RefreshGrant;
import com.mcpscanner.auth.grant.RefreshGrantStore;
import com.mcpscanner.auth.oauth.AuthState;
import com.mcpscanner.auth.oauth.InsufficientScopeReporter;
import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.auth.oauth.SharedOAuthSession;
import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.oauth.OAuthTokenRefreshPolicy;
import com.mcpscanner.core.oauth.RegistrationMode;
import com.nimbusds.oauth2.sdk.token.RefreshToken;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public final class OAuthAuthCodeStrategy implements AuthStrategy, RefreshGrantStore {

    private static final String GRANT_LEASED =
            "OAuth refresh deferred: a refresh-grant check is using the live grant; the request keeps the current token";

    private final URI issuer;
    private final String clientId;
    private final OAuthTokenRefreshPolicy refreshPolicy;
    private final SharedOAuthSession sharedSession;
    private final com.mcpscanner.core.connection.CredentialProvider credentialProvider;
    private final McpEventLog eventLog;
    private final RegistrationMode registrationMode;
    private final InsufficientScopeReporter scopeReporter;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private final ReentrantLock grantLease = new ReentrantLock();
    private volatile Runnable terminalFailureListener = () -> {};

    public OAuthAuthCodeStrategy(URI issuer, String clientId,
                                 SharedOAuthSession sharedSession,
                                 Clock clock, McpEventLog eventLog) {
        this(issuer, clientId, sharedSession,
                clock, eventLog, ignored -> com.mcpscanner.core.oauth.AuthorizationApproval.REQUIRE_PROMPT);
    }

    public OAuthAuthCodeStrategy(URI issuer, String clientId,
                                 SharedOAuthSession sharedSession,
                                 Clock clock, McpEventLog eventLog,
                                 java.util.function.Function<java.util.Set<String>, com.mcpscanner.core.oauth.AuthorizationApproval> approval) {
        this(issuer, clientId, sharedSession, clock, eventLog, approval, null);
    }

    public OAuthAuthCodeStrategy(URI issuer, String clientId,
                                 SharedOAuthSession sharedSession,
                                 Clock clock, McpEventLog eventLog,
                                 java.util.function.Function<java.util.Set<String>, com.mcpscanner.core.oauth.AuthorizationApproval> approval,
                                 RegistrationMode registrationMode) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.sharedSession = java.util.Objects.requireNonNull(sharedSession);
        this.refreshPolicy = new OAuthTokenRefreshPolicy(clock);
        this.eventLog = eventLog;
        this.registrationMode = registrationMode;
        this.scopeReporter = new InsufficientScopeReporter(eventLog, sharedSession::grantedScopes);
        this.credentialProvider = new RefreshingCredentialProvider(sharedSession.credentialProvider(
                this::requireRefresh, approval, ignored -> {}));
    }

    private void requireRefresh() {
        if (!refresh()) {
            throw refreshFailed();
        }
    }

    private static OAuthException refreshFailed() {
        return new OAuthException("OAuth refresh failed; reconnect to re-authorize");
    }

    private final class RefreshingCredentialProvider implements com.mcpscanner.core.connection.CredentialProvider {
        private final com.mcpscanner.core.connection.CredentialProvider delegate;

        private RefreshingCredentialProvider(com.mcpscanner.core.connection.CredentialProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<String, String> headers() {
            guardedRefresh(false);
            if (refreshPolicy.terminallyFailed()) {
                throw refreshFailed();
            }
            return delegate.headers();
        }

        @Override
        public com.mcpscanner.core.connection.OperationRecovery beginOperation(String operation) {
            return scopeReporter.watch(operation, delegate.beginOperation(operation));
        }
    }

    public java.util.Optional<RegistrationMode> registrationMode() {
        return java.util.Optional.ofNullable(registrationMode);
    }

    @Override
    public java.util.Optional<com.mcpscanner.core.connection.CredentialProvider> sharedCredentialProvider() {
        return java.util.Optional.ofNullable(credentialProvider);
    }

    @Override
    public Map<String, String> headers() {
        guardedRefresh(false);
        return Map.of(AuthHeaders.AUTHORIZATION_HEADER,
                AuthHeaders.BEARER_PREFIX + sharedSession.currentTokens().accessToken().getValue());
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        return guardedRefresh(true);
    }

    @Override
    public boolean renew() {
        if (grantLeasedToACheck()) {
            eventLog.warn(GRANT_LEASED);
            return false;
        }
        if (refreshToken() != null && refresh()) {
            return true;
        }
        return reauthorize();
    }

    @Override
    public java.util.Optional<AccessToken> currentToken() {
        try {
            var tokens = sharedSession.currentTokens();
            return java.util.Optional.of(AccessToken.of(tokens.accessToken().getValue(), tokens.expiresAt()));
        } catch (OAuthException noCredential) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public Optional<SignedInTokens> signedInTokens() {
        try {
            var tokens = sharedSession.currentTokens();
            return Optional.of(new SignedInTokens(Optional.ofNullable(tokens.subject()),
                    AccessToken.of(tokens.accessToken().getValue(), tokens.expiresAt()),
                    Optional.ofNullable(tokens.refreshToken()).map(RefreshToken::getValue)));
        } catch (OAuthException noCredential) {
            return Optional.empty();
        }
    }

    private boolean reauthorize() {
        try {
            sharedSession.reauthorize();
            refreshPolicy.recordSuccess();
            return true;
        } catch (OAuthException e) {
            eventLog.error("OAuth re-authorization failed: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean guardedRefresh(boolean force) {
        refreshLock.lock();
        try {
            return grantLeasedToACheck() ? deferredWhileLeased(force) : refreshUnlessHealthy(force);
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean deferredWhileLeased(boolean force) {
        if (force) {
            eventLog.warn(GRANT_LEASED);
        }
        return !force;
    }

    private boolean grantLeasedToACheck() {
        return grantLease.isLocked() && !grantLease.isHeldByCurrentThread();
    }

    private boolean refreshUnlessHealthy(boolean force) {
        if (refreshPolicy.terminallyFailed()) {
            return false;
        }
        if (!refreshPolicy.shouldAttempt(force, sharedSession.currentTokens().expiresAt())) {
            return true;
        }
        try {
            performTokenRefresh();
            refreshPolicy.recordSuccess();
            return true;
        } catch (OAuthException e) {
            int failures = refreshPolicy.recordFailure();
            eventLog.error("OAuth refresh failed (" + failures + "/"
                    + refreshPolicy.maxConsecutiveFailures() + "): " + e.getMessage(), e);
            if (refreshPolicy.terminallyFailed()) {
                tripCircuitBreaker();
            }
            return false;
        }
    }

    public boolean isTerminallyFailed() {
        return refreshPolicy.terminallyFailed();
    }

    @Override
    public Optional<RefreshGrantStore> refreshGrantStore() {
        return Optional.of(this);
    }

    @Override
    public <T> Optional<T> withExclusiveGrant(Function<LiveRefreshGrant, T> work) {
        grantLease.lock();
        try {
            awaitInFlightRefresh();
            if (refreshPolicy.terminallyFailed() || refreshToken() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(work.apply(new HeldGrant()));
        } finally {
            grantLease.unlock();
        }
    }

    private void awaitInFlightRefresh() {
        refreshLock.lock();
        refreshLock.unlock();
    }

    private RefreshGrant currentGrant() {
        var configuration = sharedSession.configuration();
        String secret = configuration.client().clientSecret();
        OAuthClient client = new OAuthClient(configuration.client().clientId(), Optional.ofNullable(secret),
                clientType(secret, configuration.tokenEndpointAuthMethod()));
        return new RefreshGrant(issuer, Optional.ofNullable(configuration.resource()), client,
                refreshToken().getValue(), requestedScopes());
    }

    private ClientType clientType(String secret, String tokenEndpointAuthMethod) {
        return registrationMode == RegistrationMode.PRE_REGISTERED
                ? ClientType.ofConfiguredSecret(secret)
                : ClientType.ofRegistration(secret, tokenEndpointAuthMethod);
    }

    private void adopt(IssuedTokens tokens) {
        sharedSession.adopt(tokens);
        refreshPolicy.recordSuccess();
        eventLog.info("OAuth tokens replaced by a refresh-grant check exchange");
    }

    private final class HeldGrant implements LiveRefreshGrant {

        @Override
        public RefreshGrant grant() {
            return currentGrant();
        }

        @Override
        public void adopt(IssuedTokens tokens) {
            OAuthAuthCodeStrategy.this.adopt(tokens);
        }
    }

    @Override
    public void setTerminalFailureListener(Runnable listener) {
        this.terminalFailureListener = listener != null ? listener : () -> {};
    }

    private void tripCircuitBreaker() {
        eventLog.error("OAuth refresh circuit breaker tripped after "
                + refreshPolicy.maxConsecutiveFailures() + " consecutive failures; "
                + "reconnect to continue");
        try {
            terminalFailureListener.run();
        } catch (RuntimeException listenerFailure) {
            eventLog.warn("OAuth terminal-failure listener threw "
                    + listenerFailure.getClass().getSimpleName()
                    + ": " + listenerFailure.getMessage());
        }
    }

    private void performTokenRefresh() {
        if (sharedSession.currentTokens().refreshToken() == null) {
            throw new OAuthException("No refresh token available; reconnect to re-authorize");
        }
        sharedSession.refresh();
    }

    @Override
    public Optional<SignInEvidence> signInEvidence() {
        return Optional.of(sharedSession.signInEvidence());
    }

    public AuthState snapshot() {
        var tokens = sharedSession.currentTokens();
        return new AuthState(tokens.subject(), tokens.expiresAt(), refreshPolicy.valid(tokens.expiresAt()));
    }

    public URI issuer() {
        return issuer;
    }

    public String clientId() {
        return clientId;
    }

    public RefreshToken refreshToken() {
        return sharedSession.currentTokens().refreshToken();
    }

    public List<String> requestedScopes() {
        return sorted(sharedSession.requestedScopes());
    }

    public List<String> grantedScopes() {
        return sorted(sharedSession.grantedScopes());
    }

    private static List<String> sorted(java.util.Set<String> scopes) {
        return scopes.stream().sorted().toList();
    }

}
