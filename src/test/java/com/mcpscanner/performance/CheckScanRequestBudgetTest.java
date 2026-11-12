package com.mcpscanner.performance;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.client.McpFixtureProfile;
import com.mcpscanner.client.McpFixtureServer;
import com.mcpscanner.client.McpHttpClients;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.LoopbackCollaborator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class CheckScanRequestBudgetTest {

    private static final String TOKEN = "alice-token";
    private static final String TOOL_CALL_WITH_TWO_STRING_ARGUMENTS = "{\"jsonrpc\":\"2.0\",\"id\":7,"
            + "\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
            + "\"arguments\":{\"path\":\"notes.txt\",\"query\":\"hello\"}}}";

    private McpFixtureServer server;
    private McpScannerSession session;
    private LoopbackCollaborator collaborator;

    @BeforeEach
    void connect() throws Exception {
        MontoyaTestFactory.install();
        server = new McpFixtureServer(McpFixtureProfile.streamable("2025-11-25", "2025-06-18"));
        session = new McpScannerSession(McpHttpClients.upstream(), McpEventLog.noop());
        session.connect(new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP,
                new BearerTokenAuthStrategy(TOKEN)));
        collaborator = LoopbackCollaborator.start();
    }

    @AfterEach
    void disconnect() {
        collaborator.close();
        session.disconnect();
        server.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogCheckIds")
    void oneToolCallScanStaysWithinTheCheckBudget(String checkId) {
        RecordingRealHttp http = new RecordingRealHttp();
        HttpRequestResponse base = http.sendRequest(toolCall());
        server.forgetRequests();

        new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                .run(catalog().find(checkId).orElseThrow(), List.of(base));

        RequestBudgets.assertWithin("Scan of one tool call, " + checkId, server.requests());
    }

    static Stream<String> catalogCheckIds() {
        return CheckCatalog.discover(CheckServices.standalone()).all().stream()
                .map(check -> check.descriptor().id())
                .filter(id -> !id.startsWith("fixture"));
    }

    private CheckCatalog catalog() {
        return CheckCatalog.discover(new CheckServices(McpEventLog.noop(), () -> new BearerTokenAuthStrategy(TOKEN),
                () -> Optional.of(collaborator), Optional::empty, PrincipalRoster::empty, AccessLedger.noop(),
                PrincipalListings.unknown()));
    }

    private HttpRequest toolCall() {
        HttpRequest request = HttpRequest.httpRequestFromUrl(server.endpoint())
                .withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream")
                .withHeader("Authorization", "Bearer " + TOKEN);
        for (var header : session.scannerHeaders().entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        return request.withBody(TOOL_CALL_WITH_TWO_STRING_ARGUMENTS);
    }
}
