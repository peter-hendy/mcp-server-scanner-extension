package com.mcpscanner.client;

import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectFollowingTransportTest {

    private static final URI ORIGIN = URI.create("https://mcp.example.com/mcp");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final McpEventLog eventLog = new McpEventLog(null);
    private final ScriptedTransport delegate = new ScriptedTransport();

    @Test
    void returnsNonRedirectUnchanged() throws Exception {
        TrackedBody body = delegate.respond(200, Map.of(), "{}");

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isSameAs(body);
        assertThat(response.uri()).isEqualTo(ORIGIN);
        assertThat(delegate.sent).hasSize(1);
    }

    @Test
    void followsSameOriginTemporaryRedirectKeepingMethodBodyAndHeaders() throws Exception {
        delegate.respond(307, location("https://mcp.example.com/v2"), "");
        delegate.respond(200, Map.of(), "{}");

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        ProtocolHttpRequest hop = delegate.sent.get(1);
        assertThat(hop.uri()).isEqualTo(URI.create("https://mcp.example.com/v2"));
        assertThat(hop.method()).isEqualTo("POST");
        assertThat(hop.body()).isEqualTo("{\"jsonrpc\":\"2.0\"}");
        assertThat(hop.headers()).containsEntry("Content-Type", "application/json")
                .containsEntry("Authorization", "Bearer t");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.uri()).isEqualTo(URI.create("https://mcp.example.com/v2"));
    }

    @Test
    void foundOnPostSwitchesToGetAndDropsBodyAndBodyHeaders() throws Exception {
        delegate.respond(302, location("https://mcp.example.com/v2"), "");
        delegate.respond(200, Map.of(), "{}");

        transport(RedirectMode.SAME_ORIGIN).send(post(), TIMEOUT);

        ProtocolHttpRequest hop = delegate.sent.get(1);
        assertThat(hop.method()).isEqualTo("GET");
        assertThat(hop.body()).isNull();
        assertThat(hop.headers()).doesNotContainKeys("Content-Type", "Content-Length")
                .containsEntry("Authorization", "Bearer t");
    }

    @Test
    void resolvesRelativeLocationAgainstCurrentUri() throws Exception {
        delegate.respond(307, location("/other/path"), "");
        delegate.respond(200, Map.of(), "{}");

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(delegate.sent.get(1).uri()).isEqualTo(URI.create("https://mcp.example.com/other/path"));
        assertThat(response.uri()).isEqualTo(URI.create("https://mcp.example.com/other/path"));
    }

    @Test
    void blocksCrossOriginRedirectUnderOff() throws Exception {
        delegate.respond(307, location("https://evil.example/mcp"), "");

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(response.status()).isEqualTo(307);
        assertThat(response.uri()).isEqualTo(ORIGIN);
        assertThat(delegate.sent).hasSize(1);
    }

    @Test
    void blocksCrossOriginRedirectUnderSameOrigin() throws Exception {
        delegate.respond(307, location("https://evil.example/mcp"), "");

        ProtocolHttpResponse response = transport(RedirectMode.SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(response.status()).isEqualTo(307);
        assertThat(delegate.sent).hasSize(1);
    }

    @Test
    void followsCrossOriginRedirectUnderAlwaysAndWarns() throws Exception {
        delegate.respond(307, location("https://other.example/mcp"), "");
        delegate.respond(200, Map.of(), "{}");

        ProtocolHttpResponse response = transport(RedirectMode.ALWAYS).send(post(), TIMEOUT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.uri()).isEqualTo(URI.create("https://other.example/mcp"));
        assertThat(warnings()).contains(
                "Cross-origin redirect followed: https://mcp.example.com -> https://other.example");
    }

    @Test
    void stopsAfterMaxRedirectsAndReturnsTheLastRedirectAsIs() throws Exception {
        for (int hop = 0; hop <= RedirectPolicy.MAX_REDIRECTS + 1; hop++) {
            delegate.respond(307, location("https://mcp.example.com/hop" + hop), "");
        }

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(delegate.sent).hasSize(RedirectPolicy.MAX_REDIRECTS + 1);
        assertThat(response.status()).isEqualTo(307);
        assertThat(response.uri()).isEqualTo(URI.create("https://mcp.example.com/hop4"));
    }

    @Test
    void closesAbandonedRedirectBodies() throws Exception {
        TrackedBody first = delegate.respond(307, location("https://mcp.example.com/a"), "moved");
        TrackedBody second = delegate.respond(308, location("https://mcp.example.com/b"), "moved");
        TrackedBody last = delegate.respond(200, Map.of(), "{}");

        transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(first.closed).isTrue();
        assertThat(second.closed).isTrue();
        assertThat(last.closed).isFalse();
    }

    @Test
    void followsRedirectEvenWhenClosingTheAbandonedBodyFails() throws Exception {
        TrackedBody abandoned = delegate.respond(307, location("https://mcp.example.com/v2"), "moved");
        abandoned.failOnClose = true;
        delegate.respond(200, Map.of(), "{}");

        ProtocolHttpResponse response = transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.uri()).isEqualTo(URI.create("https://mcp.example.com/v2"));
    }

    @Test
    void passesTheTimeoutToEveryHop() throws Exception {
        delegate.respond(307, location("https://mcp.example.com/v2"), "");
        delegate.respond(200, Map.of(), "{}");

        transport(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN).send(post(), TIMEOUT);

        assertThat(delegate.timeouts).containsExactly(TIMEOUT, TIMEOUT);
    }

    private RedirectFollowingTransport transport(RedirectMode mode) {
        return new RedirectFollowingTransport(delegate, new RedirectPolicy(eventLog, () -> mode));
    }

    private static ProtocolHttpRequest post() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Content-Length", "17");
        headers.put("Authorization", "Bearer t");
        return new ProtocolHttpRequest(ORIGIN, "POST", headers, "{\"jsonrpc\":\"2.0\"}");
    }

    private static Map<String, List<String>> location(String target) {
        return Map.of("Location", List.of(target));
    }

    private List<String> warnings() {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.WARN)
                .map(McpEventLog.LogEntry::message)
                .toList();
    }

    private static final class ScriptedTransport implements ProtocolHttpTransport {
        private final Deque<ScriptedResponse> responses = new ArrayDeque<>();
        private final List<ProtocolHttpRequest> sent = new ArrayList<>();
        private final List<Duration> timeouts = new ArrayList<>();

        TrackedBody respond(int status, Map<String, List<String>> headers, String body) {
            TrackedBody tracked = new TrackedBody(body);
            responses.add(new ScriptedResponse(status, headers, tracked));
            return tracked;
        }

        @Override
        public ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout) {
            sent.add(request);
            timeouts.add(timeout);
            ScriptedResponse next = responses.removeFirst();
            return new ProtocolHttpResponse(next.status(), next.headers(), next.body(), request.uri());
        }
    }

    private record ScriptedResponse(int status, Map<String, List<String>> headers, TrackedBody body) {}

    private static final class TrackedBody extends ByteArrayInputStream {
        private boolean closed;
        private boolean failOnClose;

        TrackedBody(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failOnClose) throw new IOException("connection reset");
        }
    }
}
