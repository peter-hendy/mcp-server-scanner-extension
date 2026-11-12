package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.BrowserLauncher;
import com.mcpscanner.auth.oauth.CallbackListenerFactory;
import com.mcpscanner.auth.oauth.OAuthAuthorizationFlow;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.DiscoverySource;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.AuthenticatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class OAuthIdentitySignInTest {

    private final List<URI> browserVisits = new CopyOnWriteArrayList<>();
    private AuthenticatedMcpServer server;
    private final List<URI> discoveredFrom = new CopyOnWriteArrayList<>();
    private OAuthAuthorizationFlow flow;
    private OAuthIdentitySignIn signIn;

    @BeforeEach
    void setUp() throws IOException {
        MontoyaTestFactory.install();
        server = AuthenticatedMcpServer.start();
        server.requireIssuedAccessToken();
        SuspiciousDestinationGate allowFixture = (url, purpose) -> SuspiciousDestinationGate.Decision.allow();
        OAuthAuthorizationFlow flow = new OAuthAuthorizationFlow(CallbackListenerFactory.defaultFactory(),
                BrowserLauncher.promptingLogin(this::approveInBrowser), Clock.systemUTC(), allowFixture,
                McpEventLog.noop(), new RecordingRealHttp());
        this.flow = flow;
        signIn = signInAt(server.endpoint());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void eachIdentityGetsItsOwnTokenFromAFreshBrowserLogin() {
        AuthStrategy bob = signIn.signIn(identity("bob"), hints());
        AuthStrategy carol = signIn.signIn(identity("carol"), hints());

        assertThat(bob).isNotSameAs(carol);
        assertThat(bob.headers().get("Authorization")).startsWith("Bearer access-");
        assertThat(bob.supportsRefresh()).isTrue();
        assertThat(server.tokenCalls()).hasSize(2);
        assertThat(browserVisits).hasSize(2)
                .allSatisfy(visit -> assertThat(visit.getRawQuery()).endsWith("&prompt=login"));
    }

    @Test
    void aBlankIssuerIsDiscoveredFromTheEndpointLikeConnectDoes() {
        AuthStrategy bob = signIn.signIn(identity("bob"), hintsWithIssuer(null));

        assertThat(discoveredFrom).containsExactly(URI.create(server.endpoint()));
        assertThat(bob.headers().get("Authorization")).startsWith("Bearer access-");
    }

    @Test
    void aBlankIssuerWithoutAnEndpointSaysWhatIsMissing() {
        assertThatThrownBy(() -> signInAt("").signIn(identity("bob"), hintsWithIssuer(null)))
                .hasMessage(OAuthIdentitySignIn.NO_ISSUER_OR_ENDPOINT);
        assertThat(discoveredFrom).isEmpty();
    }

    @Test
    void eachSignInBeginsANewAttemptBeforeDiscoveringTheIssuer() {
        List<String> events = new CopyOnWriteArrayList<>();
        IssuerDiscovery discovery = resource -> {
            events.add("discover");
            return new DiscoveredMetadata(URI.create(server.origin()), DiscoverySource.AS_WELL_KNOWN, null);
        };
        OAuthIdentitySignIn attempts = new OAuthIdentitySignIn(flow, () -> URI.create(server.endpoint()), discovery,
                ScopeApprovals.promptRequired(), Clock.systemUTC(), McpEventLog.noop(), () -> events.add("begin"));

        attempts.signIn(identity("bob"), hintsWithIssuer(null));
        attempts.signIn(identity("carol"), hintsWithIssuer(null));

        assertThat(events).containsExactly("begin", "discover", "begin", "discover");
    }

    private OAuthIdentitySignIn signInAt(String endpoint) {
        IssuerDiscovery discovery = resource -> {
            discoveredFrom.add(resource);
            return new DiscoveredMetadata(URI.create(server.origin()), DiscoverySource.AS_WELL_KNOWN, null);
        };
        return new OAuthIdentitySignIn(flow, () -> URI.create(endpoint), discovery, ScopeApprovals.promptRequired(),
                Clock.systemUTC(), McpEventLog.noop());
    }

    private OAuthClientHints hintsWithIssuer(URI issuer) {
        return new OAuthClientHints(issuer, List.of(), null, null, true,
                OAuthClientHints.DEFAULT_REDIRECT_PORT, Duration.ofSeconds(10));
    }

    private OAuthClientHints hints() {
        return new OAuthClientHints(URI.create(server.origin()), List.of(), null, null, true,
                OAuthClientHints.DEFAULT_REDIRECT_PORT, Duration.ofSeconds(10));
    }

    private static Identity identity(String name) {
        return Identity.create(name, IdentityRole.PEER, IdentityAuthKind.OAUTH);
    }

    private void approveInBrowser(URI authorization) {
        browserVisits.add(authorization);
        Map<String, String> parameters = new HashMap<>();
        Arrays.stream(authorization.getRawQuery().split("&")).forEach(pair -> {
            String[] keyAndValue = pair.split("=", 2);
            parameters.put(keyAndValue[0], URLDecoder.decode(keyAndValue[1], StandardCharsets.UTF_8));
        });
        URI callback = URI.create(parameters.get("redirect_uri") + "?code=authorization-code&state="
                + URLEncoder.encode(parameters.get("state"), StandardCharsets.UTF_8));
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(callback).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
