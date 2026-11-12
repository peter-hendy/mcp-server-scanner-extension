package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.AuthenticatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;
import com.mcpscanner.core.oauth.EnterpriseIdentityRequest;
import com.mcpscanner.core.oauth.TokenResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EnterpriseServiceLoaderE2ETest {
    private static final List<EnterpriseIdentityRequest> IDENTITY_REQUESTS = new CopyOnWriteArrayList<>();

    public static final class TestOrganization implements EnterpriseIdentityProviderRegistration {
        public String id() { return "test-organization"; }
        public EnterpriseIdentityProvider provider() {
            return request -> {
                IDENTITY_REQUESTS.add(request);
                return new TokenResponse(200, Map.of("access_token", "id-jag-assertion", "token_type", "N_A",
                        "expires_in", 3600, "issued_token_type", "urn:ietf:params:oauth:token-type:id-jag"));
            };
        }
    }

    @TempDir Path installedPlugin;
    private AuthenticatedMcpServer server;
    private McpScannerSession session;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        IDENTITY_REQUESTS.clear();
        server = AuthenticatedMcpServer.start();
        server.requireIssuedAccessToken();
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null));
    }

    @AfterEach
    void stopServer() {
        session.disconnect();
        server.close();
    }

    @Test
    void installedProviderIsDiscoveredByServiceLoaderAndDrivesTheFullIdJagConnect() throws Exception {
        var registry = EnterpriseIdentityProviderRegistry.load(pluginLoader());
        assertThat(registry.ids()).containsExactly("test-organization");

        AuthStrategy strategy = factory(registry).create(URI.create(server.endpoint()), settings());
        session.connect(new McpServerConfig(server.endpoint(), TransportType.MODERN_HTTP, strategy));

        assertThat(IDENTITY_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.form()).containsEntry("resource", server.endpoint()));
        assertThat(server.tokenCalls()).singleElement().satisfies(call ->
                assertThat(call.form()).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                        .containsEntry("assertion", "id-jag-assertion"));
        assertThat(session.catalog("tools/list", null).result().toString()).contains(AuthenticatedMcpServer.TOOL_NAME);
        assertThat(server.mcpCalls()).filteredOn(call -> call.status() != 401)
                .allSatisfy(call -> assertThat(call.header("Authorization")).isEqualTo("Bearer access-1"));
    }

    @Test
    void noInstalledProviderMeansNoIdsAndConnectIsRefusedBeforeAnyNetworkCall() {
        var registry = EnterpriseIdentityProviderRegistry.loadOrEmpty();
        assertThat(registry.ids()).isEmpty();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                factory(registry).create(URI.create(server.endpoint()), settings()))
                .hasMessageContaining("registered enterprise identity provider");
        assertThat(server.tokenCalls()).isEmpty();
        assertThat(server.mcpCalls()).isEmpty();
    }

    private ClassLoader pluginLoader() throws IOException {
        Path services = Files.createDirectories(installedPlugin.resolve("META-INF/services"));
        Files.writeString(services.resolve(EnterpriseIdentityProviderRegistration.class.getName()),
                TestOrganization.class.getName());
        return new URLClassLoader(new URL[]{installedPlugin.toUri().toURL()}, getClass().getClassLoader());
    }

    private OptionalGrantAuthFactory factory(EnterpriseIdentityProviderRegistry registry) {
        SuspiciousDestinationGate allowLoopback = (url, purpose) -> SuspiciousDestinationGate.Decision.allow();
        return new OptionalGrantAuthFactory(new BurpHttpRequestSender(new RecordingRealHttp()), allowLoopback,
                registry, Clock.systemUTC());
    }

    private OptionalGrantSettings settings() {
        return new OptionalGrantSettings(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED, URI.create(server.origin()),
                "scanner", "client-secret", Set.of(), ClientCredentialsAuthentication.basic(), "test-organization");
    }
}
