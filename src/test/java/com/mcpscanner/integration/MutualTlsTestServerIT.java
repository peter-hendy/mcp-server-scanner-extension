package com.mcpscanner.integration;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.JdkProtocolHttpTransport;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.CausalChain;
import com.mcpscanner.testutil.ClientCertificateBurpHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.TestPki;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.Optional;

import static com.mcpscanner.testutil.MutualTlsFixtures.burpTransport;
import static com.mcpscanner.testutil.MutualTlsFixtures.jdkClientTrustingCa;
import static com.mcpscanner.testutil.MutualTlsFixtures.managerThrough;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof against the real Python test-server started with mutual TLS: Connect over the
 * Burp-backed transport succeeds only when Burp presents a client certificate signed by the CA the
 * server trusts, and the plain JDK transport (which has no access to Burp's client certificates)
 * cannot connect at all.
 */
@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
@Timeout(120)
class MutualTlsTestServerIT {

    private static final String DEFAULT_TOOL = "echo";

    @TempDir
    static Path pkiDirectory;
    private static TestPki pki;
    private static McpCheckItSupport.RunningServer server;

    private final McpEventLog eventLog = new McpEventLog(null);
    private McpClientManager manager;

    @BeforeAll
    static void startServerRequiringClientCertificate() throws Exception {
        pki = TestPki.generate(pkiDirectory);
        TestPki.ServerPemFiles pem = pki.exportServerPem();
        McpCheckItSupport.Listener https =
                McpCheckItSupport.Listener.https(pki.clientTrustingCa(Optional.of(pki.clientIdentity())));
        server = McpCheckItSupport.startServer(https,
                "--tls-cert", pem.certificate().toString(),
                "--tls-key", pem.privateKey().toString(),
                "--tls-client-ca", pem.clientCa().toString());
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        if (manager != null) {
            manager.disconnect();
        }
        eventLog.shutdown();
    }

    @Test
    void burpPresentingTheClientCertificateConnectsAndDiscoversTools() throws Exception {
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.presenting(pki.clientIdentity())));

        ConnectResult result = manager.connect(streamableConfig());

        assertThat(result.tools()).extracting(McpToolDefinition::name).contains(DEFAULT_TOOL);
        assertThat(manager.scannerSession().scannerHeaders()).containsKey("Mcp-Session-Id");
    }

    @Test
    void burpWithoutClientCertificateFailsConnectWithBurpNoResponseError() throws Exception {
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.withoutClientCertificate()));

        assertThatThrownBy(() -> manager.connect(streamableConfig()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> assertThat(CausalChain.of(failure))
                        .anySatisfy(cause -> assertThat(cause)
                                .isInstanceOf(IOException.class)
                                .hasMessageStartingWith("No response from https://127.0.0.1:")
                                .hasMessageContaining("through Burp")));
    }

    @Test
    void jdkTransportTrustingTheServerFailsWithoutTheClientCertificate() throws Exception {
        manager = managerThrough(eventLog, new JdkProtocolHttpTransport(jdkClientTrustingCa(pki)));

        assertThatThrownBy(() -> manager.connect(streamableConfig()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> assertThat(CausalChain.of(failure))
                        .as("TLS 1.3 rejects the missing client certificate after the client handshake completes, "
                                + "surfacing as a certificate_required alert or a connection reset, never a reply")
                        .anyMatch(IOException.class::isInstance)
                        .noneMatch(HttpTimeoutException.class::isInstance));
    }

    private static McpServerConfig streamableConfig() {
        return new McpServerConfig(server.mcpEndpoint(), TransportType.STREAMABLE_HTTP, new NoAuthStrategy());
    }
}
