package com.mcpscanner.checks.api;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.catalog.fixturereflection.ReflectedMarkerCheck;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckRunnerLiveTargetTest {

    private static final String TOOLS_CALL = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hello\",\"count\":3}}}";

    private HttpServer server;
    private RecordingRealHttp http;
    private CheckRunner runner;
    private CheckCatalog catalog;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = new RecordingRealHttp();
        runner = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints);
        catalog = CheckCatalog.discover(CheckServices.standalone());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void insertionPointCheckFiresAgainstAVulnerableLiveTarget() throws IOException {
        HttpRequestResponse base = baseRequestAgainst(startServer(true));

        List<AuditIssue> issues = runner.run(check("fixture-reflected-marker"), List.of(base));

        assertThat(issues).isNotEmpty();
        assertThat(issues).allMatch(issue -> issue.name().equals("Fixture reflected marker"));
        assertThat(http.sentUrls).hasSizeGreaterThan(1);
    }

    @Test
    void insertionPointCheckStaysSilentAgainstASafeLiveTarget() throws IOException {
        HttpRequestResponse base = baseRequestAgainst(startServer(false));

        assertThat(runner.run(check("fixture-reflected-marker"), List.of(base))).isEmpty();
    }

    @Test
    void requestCheckReplaysEachBaseRequestAgainstTheLiveTarget() throws IOException {
        String url = startServer(false);
        HttpRequestResponse base = baseRequestAgainst(url);
        int sentBeforeRun = http.sentUrls.size();

        List<AuditIssue> issues = runner.run(check("fixture-resend-succeeds"), List.of(base, base));

        assertThat(issues).hasSize(2);
        assertThat(http.sentUrls.size() - sentBeforeRun).isEqualTo(2);
    }

    @Test
    void hostCheckRunsOnceForTheLiveTargetHost() throws IOException {
        HttpRequestResponse base = baseRequestAgainst(startServer(true));

        List<AuditIssue> issues = runner.run(check("fixture-server-banner"), List.of(base, base, base));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).detail()).isEqualTo("fixture-mcp/1.0");
    }

    private McpCheck check(String id) {
        return catalog.find(id).orElseThrow();
    }

    private HttpRequestResponse baseRequestAgainst(String url) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(url)
                .withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withBody(TOOLS_CALL);
        return http.sendRequest(request);
    }

    private String startServer(boolean vulnerable) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> respond(exchange, vulnerable));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    private static void respond(HttpExchange exchange, boolean vulnerable) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String text = vulnerable && requestBody.contains(ReflectedMarkerCheck.MARKER)
                ? "echo " + ReflectedMarkerCheck.MARKER : "ok";
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]}}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (vulnerable) {
            exchange.getResponseHeaders().add("X-Fixture-Server", "fixture-mcp/1.0");
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
