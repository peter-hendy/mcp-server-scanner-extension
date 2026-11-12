package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.status;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.withHeader;
import static org.assertj.core.api.Assertions.assertThat;

class ProtectedResourceLocatorTest {

    private static final URI MCP = URI.create("https://mcp.example.com/mcp");
    private static final String SUFFIXED = "https://mcp.example.com/.well-known/oauth-protected-resource/mcp";
    private static final String ROOT = "https://mcp.example.com/.well-known/oauth-protected-resource";
    private static final String CHALLENGED = "https://meta.example.com/prm";
    private static final String DOCUMENT = "{\"resource\":\"https://mcp.example.com/mcp\","
            + "\"authorization_servers\":[\"https://auth.example.com\",\"https://other.example.com\"],"
            + "\"scopes_supported\":[\"read\",\"write\"]}";

    private ScriptedHttp http;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = new ScriptedHttp();
    }

    private Optional<ProtectedResource> locate(URI mcpEndpoint) {
        return locate(mcpEndpoint, BearerChallenge.none());
    }

    private Optional<ProtectedResource> locate(URI mcpEndpoint, BearerChallenge challenge) {
        return new ProtectedResourceLocator(http.oauth()).locate(mcpEndpoint, challenge);
    }

    private void challengeWith(int status, String wwwAuthenticate) {
        http.on("POST", MCP.toString(), withHeader(status, "WWW-Authenticate", wwwAuthenticate));
    }

    @Test
    void readsThePathSuffixedDocumentFirst() {
        http.on("GET", SUFFIXED, json(200, DOCUMENT));

        assertThat(locate(MCP)).contains(new ProtectedResource(Optional.of(MCP), URI.create("https://auth.example.com"),
                Optional.of("read write")));
        assertThat(http.sentUrls()).containsExactly(SUFFIXED);
    }

    @Test
    void theBaseResponseChallengeIsReadBeforeAnyWellKnownLocation() {
        http.on("GET", CHALLENGED, json(200, DOCUMENT)).on("GET", SUFFIXED, json(200, DOCUMENT));

        assertThat(locate(MCP, BearerChallenge.from(List.of("Bearer resource_metadata=\"" + CHALLENGED + "\""))))
                .isPresent();
        assertThat(http.sentUrls()).containsExactly(CHALLENGED);
    }

    @Test
    void anUnusableBaseChallengeDocumentFallsBackToTheWellKnownLocations() {
        http.on("GET", ROOT, json(200, DOCUMENT));

        assertThat(locate(MCP, BearerChallenge.from(List.of("Bearer resource_metadata=\"" + CHALLENGED + "\""))))
                .isPresent();
        assertThat(http.sentUrls()).containsExactly(CHALLENGED, SUFFIXED, ROOT);
    }

    @Test
    void fallsBackToTheRootDocument() {
        http.on("GET", SUFFIXED, status(404)).on("GET", ROOT, json(200, DOCUMENT));

        assertThat(locate(MCP)).isPresent();
        assertThat(http.sentUrls()).containsExactly(SUFFIXED, ROOT);
    }

    @Test
    void readsOnlyTheRootDocumentForAnEndpointAtTheRoot() {
        http.on("GET", ROOT, json(200, DOCUMENT));

        assertThat(locate(URI.create("https://mcp.example.com/"))).isPresent();
        assertThat(http.sentUrls()).containsExactly(ROOT);
    }

    @Test
    void requestsTheRootDocumentOnceBeforeChallengingAnEndpointAtTheRoot() {
        URI rootEndpoint = URI.create("https://mcp.example.com/");

        locate(rootEndpoint);

        assertThat(http.sentUrls()).containsExactly(ROOT, rootEndpoint.toString());
    }

    @Test
    void skipsADocumentWithoutAuthorizationServers() {
        http.on("GET", SUFFIXED, json(200, "{\"resource\":\"https://mcp.example.com/mcp\"}"))
                .on("GET", ROOT, json(200, DOCUMENT));

        assertThat(locate(MCP)).isPresent();
        assertThat(http.sentUrls()).containsExactly(SUFFIXED, ROOT);
    }

    @Test
    void skipsADocumentWhoseAuthorizationServersAreNotWebUris() {
        http.on("GET", SUFFIXED, json(200, "{\"authorization_servers\":[\"urn:example:as\"]}"));

        assertThat(locate(MCP)).isEmpty();
    }

    @Test
    void usesTheFirstWebAuthorizationServer() {
        http.on("GET", SUFFIXED, json(200,
                "{\"authorization_servers\":[\"urn:example:as\",\"https://auth.example.com\"]}"));

        assertThat(locate(MCP)).map(ProtectedResource::authorizationServer)
                .contains(URI.create("https://auth.example.com"));
    }

    @Test
    void leavesResourceAndScopeEmptyWhenTheDocumentOmitsThem() {
        http.on("GET", SUFFIXED, json(200, "{\"authorization_servers\":[\"https://auth.example.com\"]}"));

        assertThat(locate(MCP)).contains(new ProtectedResource(Optional.empty(),
                URI.create("https://auth.example.com"), Optional.empty()));
    }

    @Test
    void followsTheResourceMetadataOfAnAnonymousPingChallenge() {
        challengeWith(401, "Bearer realm=\"mcp\", resource_metadata=\"" + CHALLENGED + "\"");
        http.on("GET", CHALLENGED, json(200, DOCUMENT));

        assertThat(locate(MCP)).isPresent();
        assertThat(http.sentUrls()).containsExactly(SUFFIXED, ROOT, MCP.toString(), CHALLENGED);
    }

    @Test
    void sendsTheAnonymousPingAsAJsonRpcPost() {
        locate(MCP);

        HttpRequest ping = http.sentTo(MCP.toString()).get(0);
        assertThat(ping.method()).isEqualTo("POST");
        assertThat(ping.headerValue("Content-Type")).isEqualTo("application/json");
        assertThat(ping.headerValue("Accept")).isEqualTo("application/json, text/event-stream");
        assertThat(ping.headerValue("Authorization")).isNull();
        assertThat(ping.bodyToString()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
    }

    @Test
    void matchesTheResourceMetadataParameterCaseInsensitively() {
        challengeWith(401, "Bearer RESOURCE_METADATA = \"" + CHALLENGED + "\"");
        http.on("GET", CHALLENGED, json(200, DOCUMENT));

        assertThat(locate(MCP)).isPresent();
    }

    @Test
    void ignoresAChallengeThatIsNot401() {
        challengeWith(403, "Bearer resource_metadata=\"" + CHALLENGED + "\"");
        http.on("GET", CHALLENGED, json(200, DOCUMENT));

        assertThat(locate(MCP)).isEmpty();
        assertThat(http.sentUrls()).doesNotContain(CHALLENGED);
    }

    @Test
    void ignoresA401WithoutWwwAuthenticate() {
        http.on("POST", MCP.toString(), status(401));

        assertThat(locate(MCP)).isEmpty();
    }

    @Test
    void ignoresAChallengeWithoutResourceMetadata() {
        challengeWith(401, "Bearer realm=\"mcp\"");

        assertThat(locate(MCP)).isEmpty();
    }

    @Test
    void ignoresAResourceMetadataUrlThatIsNotAWebUri() {
        challengeWith(401, "Bearer resource_metadata=\"file:///etc/prm\"");

        assertThat(locate(MCP)).isEmpty();
        assertThat(http.sentUrls()).containsExactly(SUFFIXED, ROOT, MCP.toString());
    }

    @Test
    void findsNothingWhenEverySourceIsSilent() {
        assertThat(locate(MCP)).isEmpty();
    }
}
