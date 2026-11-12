package com.mcpscanner.cvetargets.oauth;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.oauth.CallbackListenerFactory;
import com.mcpscanner.auth.oauth.OAuthAuthorizationFlow;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.auth.oauth.OAuthSession;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.cvetargets.manifest.AuthorizationCodeGrant;
import com.mcpscanner.cvetargets.manifest.OAuthClientSetup;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.mcpscanner.core.oauth.AuthorizationApproval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

final class AuthorizationCodeLogin {

    private static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(30);
    private static final SuspiciousDestinationGate LOCAL_TARGETS_ONLY =
            (url, purpose) -> SuspiciousDestinationGate.Decision.allow();

    private final AuthorizationCodeGrant grant;
    private final HarnessClientRegistration registration = new HarnessClientRegistration();

    AuthorizationCodeLogin(AuthorizationCodeGrant grant) {
        this.grant = grant;
    }

    OAuthAuthCodeStrategy login(URI armEndpoint) {
        URI issuer = URI.create(armEndpoint.getScheme() + "://" + armEndpoint.getRawAuthority() + grant.issuerPath());
        int callbackPort = freeLoopbackPort();
        OAuthAuthorizationFlow flow = new OAuthAuthorizationFlow(CallbackListenerFactory.defaultFactory(),
                new HeadlessBrowser(callbackPort), Clock.systemUTC(), LOCAL_TARGETS_ONLY, McpEventLog.noop(),
                new RecordingRealHttp());
        OAuthClientHints hints = hints(issuer, callbackPort);
        OAuthSession session = flow.connect(armEndpoint, hints);
        return new OAuthAuthCodeStrategy(issuer, session.clientId(), session.sharedSession(), Clock.systemUTC(),
                McpEventLog.noop(), ignored -> AuthorizationApproval.REQUIRE_PROMPT, session.registrationMode());
    }

    private OAuthClientHints hints(URI issuer, int callbackPort) {
        OAuthClientSetup client = grant.client();
        return switch (client.registration()) {
            case OAuthClientSetup.EXTENSION_DCR -> hints(issuer, null, null, true, callbackPort);
            case OAuthClientSetup.STATIC -> hints(issuer, client.clientId(), client.clientSecret(), false, callbackPort);
            default -> {
                HarnessClientRegistration.RegisteredClient registered = registration.register(issuer,
                        URI.create("http://127.0.0.1:" + callbackPort + "/callback"),
                        client.tokenEndpointAuthMethod(), grant.scopes());
                yield hints(issuer, registered.clientId(), registered.clientSecret(), false, callbackPort);
            }
        };
    }

    private OAuthClientHints hints(URI issuer, String clientId, String secret, boolean dcr, int callbackPort) {
        return new OAuthClientHints(issuer, grant.scopes(), clientId, secret, dcr, callbackPort, CALLBACK_TIMEOUT);
    }

    private static int freeLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
