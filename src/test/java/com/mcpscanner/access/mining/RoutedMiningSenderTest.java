package com.mcpscanner.access.mining;

import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.access.IdentityRequests;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.SendOnlyBurpHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class RoutedMiningSenderTest {

    private static final String ENDPOINT = "http://mcp.test/mcp";
    private static final Principal PRIMARY = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY,
            () -> Map.of("Authorization", "Bearer alice"), Optional.empty(), Map.of());
    private static final Principal BOB = new Principal("bob", "bob", PrincipalRole.PEER,
            () -> Map.of("Authorization", "Bearer bob"), Optional.empty(), Map.of());

    private final ScriptedHttp http = new ScriptedHttp();
    private RoutedMiningSender sender;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        MiningRequests requests = call -> ImmutableHttpRequest.post(ENDPOINT,
                Map.of("Authorization", "Bearer alice", McpHeaders.SESSION_ID, "primary-session"),
                call.method() + " " + call.params());
        sender = new RoutedMiningSender(requests, new IdentityRequests(() -> PrincipalRoster.of(PRIMARY, List.of(BOB))),
                http);
    }

    @Test
    void sendsThePrimaryAsTheConnectedSessionWithTheRenderedCall() {
        sender.send(PRIMARY, new MiningCall.ResourceRead("notes://1"));

        HttpRequest sent = http.sent.get(0);
        assertThat(sent.headerValue(McpHeaders.SESSION_ID)).isEqualTo("primary-session");
        assertThat(sent.bodyToString()).isEqualTo("resources/read {\"uri\":\"notes://1\"}");
    }

    @Test
    void routesAnotherIdentityWithItsOwnCredentialsAndTheIdentitySentinel() {
        sender.send(BOB, new MiningCall.ResourceListing());

        HttpRequest sent = http.sent.get(0);
        assertThat(sent.headerValue("Authorization")).isEqualTo("Bearer bob");
        assertThat(sent.headerValue(ScannerSentinels.IDENTITY_HEADER)).isEqualTo("bob");
        assertThat(sent.hasHeader(McpHeaders.SESSION_ID)).isFalse();
    }

    @Test
    void routesTheAnonymousUserWithoutAnyCredential() {
        sender.send(Principal.anonymous(), new MiningCall.ResourceListing());

        HttpRequest sent = http.sent.get(0);
        assertThat(sent.hasHeader("Authorization")).isFalse();
        assertThat(sent.headerValue(ScannerSentinels.IDENTITY_HEADER)).isEqualTo(Principal.ANONYMOUS_ID);
    }

    @Test
    void readsTheJsonRpcResultAndStatus() {
        http.reply(200, "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"structuredContent\":{\"id\":\"a\"}}}");

        MiningReply reply = sender.send(PRIMARY, new MiningCall.ResourceListing());

        assertThat(reply.usable()).isTrue();
        assertThat(reply.result().path("structuredContent").path("id").asText()).isEqualTo("a");
    }

    @Test
    void aJsonRpcErrorIsNotUsableAndTooManyRequestsIsRateLimited() {
        http.reply(200, "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32602,\"message\":\"no\"}}");
        assertThat(sender.send(PRIMARY, new MiningCall.ResourceListing()).usable()).isFalse();

        http.reply(429, "");
        assertThat(sender.send(PRIMARY, new MiningCall.ResourceListing()).rateLimited()).isTrue();
    }

    private static final class ScriptedHttp extends SendOnlyBurpHttp {
        private final List<HttpRequest> sent = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private volatile String body = "{}";

        void reply(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request) {
            return sendRequest(request, RequestOptions.requestOptions());
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions options) {
            sent.add(request);
            return HttpRequestResponse.httpRequestResponse(request, rawResponse(status, Map.of(), body.getBytes()));
        }
    }
}
