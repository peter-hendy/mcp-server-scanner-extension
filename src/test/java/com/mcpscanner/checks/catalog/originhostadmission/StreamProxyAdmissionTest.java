package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.LOOPBACK_URL;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.TOOLS_RESULT;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.admitting;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.captured;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.noForeignOrigin;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.ok;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.status;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.toolsList;
import static org.assertj.core.api.Assertions.assertThat;

class StreamProxyAdmissionTest {

    private static final String SSE_URL = "http://127.0.0.1:8765/messages?session_id=abc";
    private static final HttpService STREAM_PROXY = HttpService.httpService("127.0.0.1", 40000, false);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void anSseServerThatAdmitsAnyOriginYieldsTheOriginFinding() {
        AdmissionServers.Server server = new AdmissionServers.Server(legacySse(request -> ok(TOOLS_RESULT)));

        assertThat(sseCheck().audit(captured(toolsList(SSE_URL)), server.http)).extracting(AuditIssue::name)
                .containsExactly("MCP Origin header admission");
    }

    @Test
    void anSseServerThatRefusesForeignOriginsIsSilentAfterSendingEveryOriginProbe() {
        AdmissionServers.Server server = new AdmissionServers.Server(legacySse(admitting(noForeignOrigin())));

        assertThat(sseCheck().audit(captured(toolsList(SSE_URL)), server.http)).isEmpty();
        assertThat(server.received).filteredOn(request -> request.hasHeader("Origin")).hasSize(3);
    }

    @Test
    void neverSendsAProbeHostTheSseClientWouldDrop() {
        AdmissionServers.Server server = new AdmissionServers.Server(legacySse(request -> ok(TOOLS_RESULT)));

        sseCheck().audit(captured(toolsList(SSE_URL)), server.http);

        assertThat(server.received).hasSize(5)
                .allSatisfy(request -> assertThat(request.headerValue("Host")).isEqualTo("127.0.0.1:8765"));
    }

    @Test
    void everyRequestRunsOnItsOwnStreamWithTheSessionCredentials() {
        AdmissionServers.Server server = new AdmissionServers.Server(legacySse(request -> ok(TOOLS_RESULT)));

        sseCheck().audit(captured(toolsList(SSE_URL)), server.http);

        assertThat(server.received).allSatisfy(request -> {
            assertThat(request.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)).isTrue();
            assertThat(request.hasHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER)).isFalse();
            assertThat(request.headerValue("Authorization")).isEqualTo("Bearer live");
        });
    }

    private static Function<HttpRequest, HttpResponse> legacySse(Function<HttpRequest, HttpResponse> ownStream) {
        return request -> request.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)
                ? ownStream.apply(request) : status(202, "Accepted");
    }

    private static OriginHostAdmissionCheck sseCheck() {
        return new OriginHostAdmissionCheck(McpEventLog.noop(),
                new CredentialHeaders(() -> new BearerTokenAuthStrategy("live")), () -> Optional.of(STREAM_PROXY),
                ScriptedEventStreams.none(), new ProbeSessions(NoAuthStrategy::new));
    }
}
