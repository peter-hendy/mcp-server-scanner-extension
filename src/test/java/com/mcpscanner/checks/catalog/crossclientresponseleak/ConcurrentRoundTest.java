package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.http.ForwardingHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentRoundTest {

    private static final int CLIENTS = 3;

    private final List<Thread> toolCallThreads = new CopyOnWriteArrayList<>();
    private final AtomicInteger markers = new AtomicInteger();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void callsEveryProbeClientOnANamedDaemonThread() {
        try (LeakServers.Server server = LeakServers.echoing()) {
            Http http = new ThreadRecordingHttp(server.http);
            List<ProbeClient> clients = openClients(http);

            new ConcurrentRound(() -> "m" + markers.incrementAndGet(), Duration.ofSeconds(2))
                    .run(clients, echoableCall(), LeakServers.ECHOED_ARGUMENT, RequestIds.DISTINCT);

            clients.forEach(ProbeClient::close);
        }
        assertThat(toolCallThreads).hasSize(CLIENTS).allSatisfy(thread -> {
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("mcp-cross-client-probe");
        });
    }

    private static List<ProbeClient> openClients(Http http) {
        ProbeClients clients = new ProbeClients(new ProbeSessions(NoAuthStrategy::new, Duration.ofSeconds(2)),
                CheckLog.labelled(McpEventLog.noop(), "test"));
        return clients.openAll(LeakServers.echoableToolCall(), http, CLIENTS).opened().orElseThrow();
    }

    private static EchoableCall echoableCall() {
        return EchoableCall.of(LeakServers.echoableToolCall().request()).orElseThrow();
    }

    private final class ThreadRecordingHttp extends ForwardingHttp {

        private ThreadRecordingHttp(Http delegate) {
            super(delegate);
        }

        @Override
        protected HttpRequest outgoing(HttpRequest request) {
            if (request.bodyToString().contains("\"tools/call\"")) {
                toolCallThreads.add(Thread.currentThread());
            }
            return request;
        }

        @Override
        protected HttpRequestResponse incoming(HttpRequestResponse exchange) {
            return exchange;
        }
    }
}
