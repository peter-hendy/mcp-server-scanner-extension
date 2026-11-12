package com.mcpscanner.auth.oauth;

import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.mcpscanner.core.oauth.AuthorizationCallback;
import com.mcpscanner.core.oauth.AuthorizationCodeConfig;
import com.mcpscanner.core.oauth.AuthorizationRequest;
import com.mcpscanner.core.oauth.BrowserCallback;
import com.mcpscanner.core.oauth.RegisteredClient;
import com.mcpscanner.core.oauth.TokenExchange;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

final class BrowserAuthorizationSessions {

    private final CallbackListenerFactory listenerFactory;
    private final BrowserLauncher browserLauncher;
    private final TokenExchange tokenExchange;
    private final Clock clock;
    private final McpEventLog eventLog;

    BrowserAuthorizationSessions(CallbackListenerFactory listenerFactory, BrowserLauncher browserLauncher,
                                 TokenExchange tokenExchange, Clock clock, McpEventLog eventLog) {
        this.listenerFactory = listenerFactory;
        this.browserLauncher = browserLauncher;
        this.tokenExchange = tokenExchange;
        this.clock = clock;
        this.eventLog = eventLog;
    }

    SharedOAuthSession open(URI resource, OAuthClientHints hints, AuthorizationServerMetadata metadata,
                            ClientRegistration client, CallbackListener initialListener, LoopbackCallback callback) {
        var config = new AuthorizationCodeConfig(
                hints.issuer(), resource, metadata.getAuthorizationEndpointURI(), metadata.getTokenEndpointURI(),
                new RegisteredClient(hints.issuer(), client.clientId(), client.clientSecret()),
                callback.uri(), Set.copyOf(hints.scopes()), metadata.supportsAuthorizationResponseIssuerParam());
        var unusedInitialListener = new AtomicReference<>(initialListener);
        return new SharedOAuthSession(config, tokenExchange, request -> {
            CallbackListener listener = unusedInitialListener.getAndSet(null);
            return listener != null
                    ? authorize(request, listener, callback, hints.timeout())
                    : authorizeOnReopenedListener(request, callback, hints.timeout());
        }, clock, new SignInEvidence(metadata.toJSONObject().toJSONString(), client.registrationResponseJson()));
    }

    private BrowserCallback authorizeOnReopenedListener(AuthorizationRequest request, LoopbackCallback callback,
                                                        Duration timeout) {
        try (CallbackListener listener = listenerFactory.start(callback.port(), LoopbackCallback.PATH)) {
            return authorize(request, listener, callback, timeout);
        } catch (IOException failure) {
            throw new OAuthException("Cannot reopen OAuth callback port " + callback.port()
                    + "; release the port and reconnect", failure);
        }
    }

    private BrowserCallback authorize(AuthorizationRequest request, CallbackListener listener,
                                      LoopbackCallback callback, Duration timeout) {
        eventLog.info("Opening browser to authorization endpoint");
        browserLauncher.open(request.uri());
        CallbackResult result = awaitCallback(listener, timeout);
        return new BrowserCallback(callback.uri(), new AuthorizationCallback(result.state(), result.issuer(),
                result.code(), result.error(), result.errorDescription()));
    }

    private static CallbackResult awaitCallback(CallbackListener listener, Duration timeout) {
        try {
            return listener.awaitCallback().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new OAuthException("Timed out waiting for OAuth callback after " + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("Interrupted while waiting for OAuth callback", e);
        } catch (ExecutionException e) {
            throw new OAuthException("OAuth callback failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
}
