package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SessionLifecycleLiveTargetTest {

    private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
            + "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"live-target-test\",\"version\":\"1\"}}}";
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}";

    private final RecordingRealHttp http = new RecordingRealHttp();
    private StatefulStreamableServer server;
    private String liveSessionId;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void aDeletedSessionWhoseEventStreamStillOpensIsReportedWithTheStreamAsEvidence() throws IOException {
        server = StatefulStreamableServer.keepingDeletedStreams();

        List<AuditIssue> issues = runCheck();

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(SessionLifecycleIssues.DELETED_REUSE);
            assertThat(issue.detail()).contains("event stream");
            assertThat(issue.requestResponses()).anySatisfy(this::isStreamOpenedForTheDeletedSession);
        });
        assertThat(server.streamsOpened()).isEqualTo(1);
        assertProbesCleanedUp();
    }

    @Test
    void aServerThatEndsDeletedSessionsForPostAndGetIsSilent() throws IOException {
        server = StatefulStreamableServer.endingDeletedSessions();

        List<AuditIssue> issues = runCheck();

        assertThat(issues).isEmpty();
        assertThat(server.streamRequestSessions()).containsAnyElementsOf(server.deleted());
        assertThat(server.streamsOpened()).isZero();
        assertProbesCleanedUp();
    }

    private List<AuditIssue> runCheck() {
        HttpRequestResponse base = baseRequestInLiveSession();
        CheckRunner runner = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints);
        return runner.run(CheckCatalog.discover(CheckServices.standalone()).find("session-lifecycle").orElseThrow(),
                List.of(base));
    }

    private HttpRequestResponse baseRequestInLiveSession() {
        HttpRequestResponse initialized = http.sendRequest(post(INITIALIZE));
        liveSessionId = initialized.response().headerValue("Mcp-Session-Id");
        return http.sendRequest(post(PING).withHeader("Mcp-Session-Id", liveSessionId));
    }

    private HttpRequest post(String body) {
        return HttpRequest.httpRequestFromUrl(server.url())
                .withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream")
                .withBody(body);
    }

    private void isStreamOpenedForTheDeletedSession(HttpRequestResponse exchange) {
        assertThat(exchange.request().method()).isEqualTo("GET");
        assertThat(exchange.request().headerValue("Accept")).isEqualTo("text/event-stream");
        assertThat(server.deleted()).contains(exchange.request().headerValue("Mcp-Session-Id"));
        assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
        assertThat(exchange.response().headerValue("Content-Type")).startsWith("text/event-stream");
    }

    private void assertProbesCleanedUp() {
        assertThat(server.issued()).hasSizeGreaterThan(1);
        assertThat(server.open()).containsExactly(liveSessionId);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(server.streamsOpenNow()).isZero());
    }
}
