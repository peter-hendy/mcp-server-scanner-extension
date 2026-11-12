package com.mcpscanner.auth.oauth;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class OAuthAuthorizationFlow {

    private final CallbackListenerFactory listenerFactory;
    private final McpEventLog eventLog;
    private final IssuerMetadataResolver metadataResolver;
    private final ClientRegistrar clientRegistrar;
    private final BrowserAuthorizationSessions sessions;

    public OAuthAuthorizationFlow(CallbackListenerFactory listenerFactory,
                                   BrowserLauncher browserLauncher,
                                   Clock clock,
                                   SuspiciousDestinationGate gate,
                                   McpEventLog eventLog,
                                   Http http) {
        this(listenerFactory, browserLauncher, clock, gate, eventLog, http,
                ClientIdentityProvider.defaults());
    }

    public OAuthAuthorizationFlow(CallbackListenerFactory listenerFactory,
                                   BrowserLauncher browserLauncher,
                                   Clock clock,
                                   SuspiciousDestinationGate gate,
                                   McpEventLog eventLog,
                                   Http http,
                                   ClientIdentityProvider identityProvider) {
        McpEventLog log = eventLog != null ? eventLog : McpEventLog.noop();
        BurpHttpRequestSender sender = new BurpHttpRequestSender(Objects.requireNonNull(http, "http must not be null"));
        OAuthDestinationGuard guard = new OAuthDestinationGuard(gate, log);
        this.listenerFactory = listenerFactory;
        this.eventLog = log;
        this.metadataResolver = new IssuerMetadataResolver(new BurpMetadataClient(sender), guard, log);
        this.clientRegistrar = new ClientRegistrar(sender, guard, identityProvider, log);
        this.sessions = new BrowserAuthorizationSessions(listenerFactory, browserLauncher,
                new TokenEndpointClient(sender, gate), clock, log);
    }

    public OAuthSession connect(URI mcpResource, OAuthClientHints hints) {
        return connect(mcpResource, hints, null);
    }

    public OAuthSession connect(URI mcpResource, OAuthClientHints hints,
                                AuthorizationServerMetadata preDiscovered) {
        eventLog.info("OAuth flow starting - issuer=" + hints.issuer()
                + ", dcr=" + (hints.allowDcr() && hints.clientId() == null ? "yes" : "no"));
        try (CallbackListener listener = listenerFactory.start(hints.redirectPort(), LoopbackCallback.PATH)) {
            LoopbackCallback callback = new LoopbackCallback(listener.port());
            AuthorizationServerMetadata metadata = metadataResolver.resolve(hints.issuer(), mcpResource, preDiscovered);
            ClientRegistration client = clientRegistrar.register(metadata, hints, callback, mcpResource);
            SharedOAuthSession session = sessions.open(mcpResource, hints, metadata, client, listener, callback);
            return new OAuthSession(authenticate(session), client.clientId(), client.clientSecret(),
                    client.registrationAccessToken(), client.registrationClientUri(), session,
                    client.registrationMode(), Optional.ofNullable(metadata.getRegistrationEndpointURI()));
        } catch (IOException e) {
            throw new OAuthException("Failed to start callback listener on port " + hints.redirectPort()
                    + ": " + e.getMessage(), e);
        }
    }

    private OAuthTokens authenticate(SharedOAuthSession session) {
        try {
            OAuthTokens tokens = session.authenticate();
            eventLog.info("Token exchange succeeded");
            session.scopeGrant().withheldWarning().ifPresent(eventLog::warn);
            return tokens;
        } catch (OAuthException failure) {
            eventLog.error("OAuth callback rejected or token exchange failed: " + failure.getMessage());
            throw failure;
        }
    }
}
