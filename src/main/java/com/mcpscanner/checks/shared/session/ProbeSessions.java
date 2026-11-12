package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.StrategyCredentials;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.mcp.SseProbeStreams;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.connection.McpConnection;
import com.mcpscanner.core.connection.SessionDeletePort;
import com.mcpscanner.core.connection.SseSessionIds;
import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpFlow;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ProbeSessions {

    private static final int MAX_FLOW_MESSAGES = 32;
    private static final ClientProfile PROBE_CLIENT = ClientProfile.of("mcp-server-scanner-probe", "1");

    private final Supplier<AuthStrategy> authStrategy;
    private final Optional<Duration> responseTimeout;
    private final StreamingFallback streaming;

    public ProbeSessions(Supplier<AuthStrategy> authStrategy) {
        this(authStrategy, Optional.empty());
    }

    public ProbeSessions(Supplier<AuthStrategy> authStrategy, Duration responseTimeout) {
        this(authStrategy, Optional.of(responseTimeout));
    }

    private ProbeSessions(Supplier<AuthStrategy> authStrategy, Optional<Duration> responseTimeout) {
        this.authStrategy = authStrategy;
        this.responseTimeout = responseTimeout;
        this.streaming = responseTimeout.map(StreamingFallback::outsideBurp).orElseGet(StreamingFallback::outsideBurp);
    }

    public Optional<HttpProbeSession> openHttp(HttpRequestResponse base, Http http) {
        return openHttp(base, http, exchange -> { });
    }

    public Optional<HttpProbeSession> openHttp(HttpRequestResponse base, Http http,
                                               Consumer<HttpRequestResponse> exchanges) {
        return endpoint(base, http, exchanges).map(ProbeEndpoint::open);
    }

    public Optional<ProbeEndpoint> endpoint(HttpRequestResponse base, Http http,
                                            Consumer<HttpRequestResponse> exchanges) {
        String endpoint = base.request().url();
        if (SseSessionIds.from(endpoint).isPresent()) {
            return Optional.empty();
        }
        BurpSessionPorts ports = new BurpSessionPorts(http, endpoint, exchanges, requestOptions(), streaming);
        return Optional.of(new ProbeEndpoint(connection(), ports, deletesSparing(base, ports),
                PROBE_CLIENT, HandshakeVersion.of(base.request())));
    }

    public SseProbeEndpoint sseEndpoint(HttpRequestResponse base, Http http,
                                                  Consumer<HttpRequestResponse> exchanges, SseProbeStreams streams) {
        RequestOptions options = requestOptions();
        return new SseProbeEndpoint(connection(), streams,
                messageUrl -> new BurpSessionPorts(http, messageUrl, exchanges, options, streaming), base.request().url(),
                PROBE_CLIENT);
    }

    private RequestOptions requestOptions() {
        RequestOptions noRedirects = RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER);
        return responseTimeout.map(timeout -> noRedirects.withResponseTimeout(timeout.toMillis())).orElse(noRedirects);
    }

    private static SessionDeletePort deletesSparing(HttpRequestResponse base, BurpSessionPorts ports) {
        return SessionHeaders.carriedBy(base.request())
                .<SessionDeletePort>map(baseSession -> new ProtectedSessionDeletes(ports::delete, baseSession))
                .orElse(ports::delete);
    }

    private McpConnection connection() {
        return new McpConnection(new McpFlow(BoundedBodyReader.MAX_RESPONSE_BYTES, MAX_FLOW_MESSAGES),
                new StrategyCredentials(authStrategy.get()));
    }
}
