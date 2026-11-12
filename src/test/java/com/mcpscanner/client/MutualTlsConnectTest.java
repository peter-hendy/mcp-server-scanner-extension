package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;
import com.mcpscanner.testutil.CausalChain;
import com.mcpscanner.testutil.ClientCertificateBurpHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.ScanProxyClient;
import com.mcpscanner.testutil.TestPki;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.mcpscanner.testutil.MutualTlsFixtures.burpTransport;
import static com.mcpscanner.testutil.MutualTlsFixtures.jdkClientTrustingCa;
import static com.mcpscanner.testutil.MutualTlsFixtures.managerThrough;

@Timeout(60)
class MutualTlsConnectTest {

    private static final String STREAMABLE_VERSION = "2025-06-18";
    private static final String SSE_VERSION = "2024-11-05";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    static Path pkiDirectory;
    private static TestPki pki;

    private final McpEventLog eventLog = new McpEventLog(null);
    private McpFixtureServer server;
    private McpClientManager manager;
    private SseProxyServer proxy;

    @BeforeAll
    static void generatePki() throws IOException, GeneralSecurityException {
        pki = TestPki.generate(pkiDirectory);
    }

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        if (proxy != null) {
            proxy.stop();
        }
        if (manager != null) {
            manager.disconnect();
        }
        if (server != null) {
            server.close();
        }
        eventLog.shutdown();
    }

    @Test
    void streamableHttpConnectPresentsBurpClientCertificateAndCapturesSession() throws Exception {
        startServer(McpFixtureProfile.streamable(STREAMABLE_VERSION));
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity())));

        ConnectResult result = manager.connect(config(TransportType.STREAMABLE_HTTP));

        assertDiscoveredEchoTool(result);
        assertThat(manager.scannerSession().scannerHeaders())
                .containsEntry("Mcp-Session-Id", "session-" + STREAMABLE_VERSION);
    }

    @Test
    void modernHttpConnectPresentsBurpClientCertificate() throws Exception {
        startServer(McpFixtureProfile.modernOnly());
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity())));

        ConnectResult result = manager.connect(config(TransportType.MODERN_HTTP));

        assertDiscoveredEchoTool(result);
        assertThat(manager.scannerSession().negotiatedProtocolVersion()).isEqualTo(McpFixtureServer.MODERN_VERSION);
    }

    @Test
    void burpWithoutClientCertificateFailsConnectWithBurpNoResponseError() throws Exception {
        startServer(McpFixtureProfile.streamable(STREAMABLE_VERSION));
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.withoutClientCertificate()));

        assertBurpTransportGotNoResponse(() -> manager.connect(config(TransportType.STREAMABLE_HTTP)));
        assertThat(server.callsTo("initialize")).isEmpty();
    }

    @Test
    void burpPresentingCertificateFromAnotherIssuerFailsConnect() throws Exception {
        startServer(McpFixtureProfile.streamable(STREAMABLE_VERSION));
        manager = managerThrough(eventLog,
                burpTransport(ClientCertificateBurpHttp.presenting(pki.untrustedClientIdentity())));

        assertBurpTransportGotNoResponse(() -> manager.connect(config(TransportType.STREAMABLE_HTTP)));
        assertThat(server.callsTo("initialize")).isEmpty();
    }

    @Test
    void jdkTransportTrustingTheServerStillFailsWithoutTheClientCertificate() throws Exception {
        startServer(McpFixtureProfile.streamable(STREAMABLE_VERSION));
        manager = managerThrough(eventLog, new JdkProtocolHttpTransport(jdkClientTrustingCa(pki)));

        assertThatThrownBy(() -> manager.connect(config(TransportType.STREAMABLE_HTTP)))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> assertThat(CausalChain.of(failure)).anyMatch(SSLException.class::isInstance));
        assertThat(server.callsTo("initialize")).isEmpty();
    }

    @Test
    void defaultJdkClientRejectsTheServerCertificateThatBurpAccepts() throws Exception {
        startServer(McpFixtureProfile.streamable(STREAMABLE_VERSION));
        ProtocolHttpTransport defaultJdk = new JdkProtocolHttpTransport(HttpClient.newHttpClient());
        ProtocolHttpRequest probe = new ProtocolHttpRequest(URI.create(server.endpoint()), "POST", Map.of(), "{}");

        assertThatThrownBy(() -> defaultJdk.send(probe, HANDSHAKE_TIMEOUT))
                .isInstanceOf(SSLHandshakeException.class)
                .hasStackTraceContaining("PKIX path building failed");
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"MODERN_HTTP", "STREAMABLE_HTTP"})
    void scannerRequestThroughTheLocalProxyReachesTheMutualTlsServerThroughBurp(TransportType transport)
            throws Exception {
        ProtocolHttpTransport burp = burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity()));
        connectThrough(transport, burp);
        proxy = startProxy(TestSseProxies.builder(manager.scannerSession()).upstreamTransport(burp).build());

        HttpResponse<String> reply = ScanProxyClient.sendScannerToolsCall(proxy.port(), McpFixtureServer.toolName());

        assertThat(reply.statusCode()).isEqualTo(200);
        assertThat(reply.body()).contains(McpFixtureServer.TOOL_RESULT_TEXT);
        assertThat(server.callsTo("tools/call")).singleElement()
                .satisfies(call -> assertThat(call.scannerCapabilityReceived()).isFalse());
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"MODERN_HTTP", "STREAMABLE_HTTP"})
    void scannerRequestThroughAJdkOnlyProxyNeverReachesTheMutualTlsServer(TransportType transport)
            throws Exception {
        connectThrough(transport, burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity())));
        proxy = startProxy(TestSseProxies.builder(manager.scannerSession()).build());

        HttpResponse<String> reply = ScanProxyClient.sendScannerToolsCall(proxy.port(), McpFixtureServer.toolName());

        assertThat(reply.statusCode()).isEqualTo(502);
        assertThat(server.callsTo("tools/call")).isEmpty();
    }

    @Test
    void sseConnectFailingTheClientCertificateHandshakeLogsTheTlsHintOnce() throws Exception {
        server = McpFixtureServer.overHttps(McpFixtureProfile.sseOnly(SSE_VERSION),
                pki.serverRequiringClientCertificate());

        assertSseConnectFailsLoggingTheTlsHintOnce();
    }

    @RepeatedTest(10)
    void sseConnectRejectedForMissingClientCertificateOnDefaultTlsLogsTheTlsHintOnce() throws Exception {
        server = McpFixtureServer.overHttps(McpFixtureProfile.sseOnly(SSE_VERSION),
                pki.serverRequiringClientCertificateOnDefaultTls());

        assertSseConnectFailsLoggingTheTlsHintOnce();
    }

    private void assertSseConnectFailsLoggingTheTlsHintOnce() throws GeneralSecurityException {
        McpScannerSession session = new McpScannerSession(jdkClientTrustingCa(pki),
                burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity())), eventLog, () -> true, ClientIdentityProvider.defaults());

        assertThatThrownBy(() -> session.connect(config(TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventLog.snapshot())
                .filteredOn(entry -> entry.message().equals(McpScannerSession.SSE_TLS_HINT))
                .hasSize(1);
    }

    private void startServer(McpFixtureProfile profile) throws IOException, GeneralSecurityException {
        server = McpFixtureServer.overHttps(profile, pki.serverRequiringClientCertificate());
    }

    private void connectThrough(TransportType transport, ProtocolHttpTransport burp)
            throws IOException, GeneralSecurityException {
        startServer(transport == TransportType.MODERN_HTTP
                ? McpFixtureProfile.modernOnly() : McpFixtureProfile.streamable(STREAMABLE_VERSION));
        manager = managerThrough(eventLog, burp);
        manager.connect(config(transport));
    }

    private static SseProxyServer startProxy(SseProxyServer unstarted) throws IOException {
        unstarted.start();
        return unstarted;
    }

    private McpServerConfig config(TransportType transport) {
        return new McpServerConfig(server.endpoint(), transport, new NoAuthStrategy());
    }

    private void assertDiscoveredEchoTool(ConnectResult result) {
        assertThat(result.tools()).extracting(McpToolDefinition::name).containsExactly(McpFixtureServer.toolName());
        assertThat(server.callsTo("tools/list")).isNotEmpty();
    }

    private static void assertBurpTransportGotNoResponse(ThrowingCallable connect) {
        assertThatThrownBy(connect)
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> assertThat(CausalChain.of(failure))
                        .anySatisfy(cause -> assertThat(cause)
                                .isInstanceOf(IOException.class)
                                .hasMessageStartingWith("No response from https://127.0.0.1:")
                                .hasMessageContaining("through Burp")));
    }
}
