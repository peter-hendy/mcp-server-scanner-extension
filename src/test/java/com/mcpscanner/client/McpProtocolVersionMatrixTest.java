package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpFixtureProfile.ReplyFraming;
import com.mcpscanner.client.McpFixtureProfile.VersionRefusal;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.mcpscanner.client.McpFixtureServer.MODERN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Timeout(30)
class McpProtocolVersionMatrixTest {
    private static final String SSE_VERSION = "2024-11-05";
    private static final String FIRST_STREAMABLE_VERSION = "2025-03-26";
    private static final String DEFAULT_STREAMABLE_VERSION = "2025-06-18";
    private static final String LATEST_STREAMABLE_VERSION = "2025-11-25";
    private static final List<String> STREAMABLE_VERSIONS =
            List.of(FIRST_STREAMABLE_VERSION, DEFAULT_STREAMABLE_VERSION, LATEST_STREAMABLE_VERSION);
    private static final String UNKNOWN_VERSION = "2099-01-01";

    private final McpClientManager manager =
            new McpClientManager(McpEventLog.noop());
    private McpFixtureServer server;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        manager.disconnect();
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest(name = "{0} {1} against {2}")
    @MethodSource("explicitTransports")
    void explicitTransportNegotiatesVersionAndDiscoversTools(TransportType transport, String version,
                                                              McpFixtureProfile profile) throws IOException {
        ConnectResult result = connect(profile, transport);

        assertNegotiated(transport, version);
        assertDiscovered(result, version);
    }

    @ParameterizedTest(name = "{0} via {1}")
    @MethodSource("versionRefusals")
    void streamableHttpAdoptsTheVersionTheServerSteersTo(String version, VersionRefusal refusal) throws IOException {
        ConnectResult result = connect(McpFixtureProfile.streamable(version).withRefusal(refusal),
                TransportType.STREAMABLE_HTTP);

        assertNegotiated(TransportType.STREAMABLE_HTTP, version);
        assertDiscovered(result, version);
        assertThat(server.callsTo("initialize")).hasSize(refusal == VersionRefusal.COUNTER_OFFER ? 2 : 1);
    }

    @ParameterizedTest(name = "{0} selects {1} {2}")
    @MethodSource("autoSelections")
    void autoSelectsTheMostModernTransportTheServerAccepts(McpFixtureProfile profile, TransportType transport,
                                                           String version) throws IOException {
        ConnectResult result = connect(profile, TransportType.AUTO);

        assertNegotiated(transport, version);
        assertDiscovered(result, version);
    }

    @ParameterizedTest(name = "{0} over {1}")
    @MethodSource("incompatibleServers")
    void incompatibleServerFailsWithoutLeavingASession(McpFixtureProfile profile, TransportType transport)
            throws IOException {
        server = new McpFixtureServer(profile);

        assertThatThrownBy(() -> manager.connect(config(transport))).isInstanceOf(IllegalStateException.class);

        McpScannerSession session = manager.scannerSession();
        assertThat(manager.isConnected()).isFalse();
        assertThat(session.transportType()).isNull();
        assertThat(session.negotiatedProtocolVersion()).isNull();
        assertThat(server.callsTo("tools/list")).isEmpty();
    }

    static Stream<Arguments> explicitTransports() {
        Stream<Arguments> http = Arrays.stream(ReplyFraming.values())
                .flatMap(McpProtocolVersionMatrixTest::httpTransportsFramedAs);
        Stream<Arguments> sse = Stream.of(
                Arguments.of(TransportType.SSE, SSE_VERSION, McpFixtureProfile.sseOnly(SSE_VERSION)),
                Arguments.of(TransportType.SSE, DEFAULT_STREAMABLE_VERSION,
                        McpFixtureProfile.sseOnly(DEFAULT_STREAMABLE_VERSION)),
                Arguments.of(TransportType.SSE, DEFAULT_STREAMABLE_VERSION,
                        McpFixtureProfile.sseOnly(DEFAULT_STREAMABLE_VERSION).requiringSseVersionHeader()));
        return Stream.concat(http, sse);
    }

    private static Stream<Arguments> httpTransportsFramedAs(ReplyFraming framing) {
        Stream<Arguments> modern = Stream.of(Arguments.of(TransportType.MODERN_HTTP, MODERN_VERSION,
                McpFixtureProfile.modernOnly().withFraming(framing)));
        Stream<Arguments> streamable = STREAMABLE_VERSIONS.stream().map(version -> Arguments.of(
                TransportType.STREAMABLE_HTTP, version, McpFixtureProfile.streamable(version).withFraming(framing)));
        Stream<Arguments> preStreamable = Stream.of(Arguments.of(TransportType.STREAMABLE_HTTP, SSE_VERSION,
                McpFixtureProfile.streamable(SSE_VERSION).withRefusal(VersionRefusal.SERVER_PREFERRED)
                        .withFraming(framing)));
        return Stream.of(modern, streamable, preStreamable).flatMap(cases -> cases);
    }

    static Stream<Arguments> versionRefusals() {
        return Stream.of(FIRST_STREAMABLE_VERSION, DEFAULT_STREAMABLE_VERSION).flatMap(version ->
                Arrays.stream(VersionRefusal.values()).map(refusal -> Arguments.of(version, refusal)));
    }

    static Stream<Arguments> autoSelections() {
        Stream<Arguments> streamable = STREAMABLE_VERSIONS.stream().map(version -> Arguments.of(
                McpFixtureProfile.streamable(version), TransportType.STREAMABLE_HTTP, version));
        Stream<Arguments> others = Stream.of(
                Arguments.of(McpFixtureProfile.modernOnly(), TransportType.MODERN_HTTP, MODERN_VERSION),
                Arguments.of(McpFixtureProfile.streamable(DEFAULT_STREAMABLE_VERSION).withModern(),
                        TransportType.MODERN_HTTP, MODERN_VERSION),
                Arguments.of(McpFixtureProfile.streamable(SSE_VERSION).withRefusal(VersionRefusal.SERVER_PREFERRED),
                        TransportType.STREAMABLE_HTTP, SSE_VERSION),
                Arguments.of(McpFixtureProfile.streamable(DEFAULT_STREAMABLE_VERSION).withSse(SSE_VERSION),
                        TransportType.STREAMABLE_HTTP, DEFAULT_STREAMABLE_VERSION),
                Arguments.of(McpFixtureProfile.sseOnly(SSE_VERSION), TransportType.SSE, SSE_VERSION),
                Arguments.of(McpFixtureProfile.sseOnly(DEFAULT_STREAMABLE_VERSION), TransportType.SSE,
                        DEFAULT_STREAMABLE_VERSION));
        return Stream.concat(streamable, others);
    }

    static Stream<Arguments> incompatibleServers() {
        McpFixtureProfile unknownVersionReply =
                McpFixtureProfile.streamable(UNKNOWN_VERSION).withRefusal(VersionRefusal.SERVER_PREFERRED);
        return Stream.of(
                incompatible("unknown version reply", unknownVersionReply, TransportType.STREAMABLE_HTTP),
                incompatible("unknown version reply", unknownVersionReply, TransportType.AUTO),
                incompatible("unknown version counter-offer", McpFixtureProfile.streamable(UNKNOWN_VERSION),
                        TransportType.STREAMABLE_HTTP),
                incompatible("streamable-only server", McpFixtureProfile.streamable(DEFAULT_STREAMABLE_VERSION),
                        TransportType.MODERN_HTTP),
                incompatible("streamable-only server", McpFixtureProfile.streamable(DEFAULT_STREAMABLE_VERSION),
                        TransportType.SSE),
                incompatible("modern-only server", McpFixtureProfile.modernOnly(), TransportType.STREAMABLE_HTTP),
                incompatible("modern-only server", McpFixtureProfile.modernOnly(), TransportType.SSE),
                incompatible("sse-only server", McpFixtureProfile.sseOnly(SSE_VERSION), TransportType.MODERN_HTTP),
                incompatible("sse-only server", McpFixtureProfile.sseOnly(SSE_VERSION), TransportType.STREAMABLE_HTTP));
    }

    private static Arguments incompatible(String description, McpFixtureProfile profile, TransportType transport) {
        return Arguments.of(Named.of(description, profile), transport);
    }

    private ConnectResult connect(McpFixtureProfile profile, TransportType transport) throws IOException {
        server = new McpFixtureServer(profile);
        return manager.connect(config(transport));
    }

    private McpServerConfig config(TransportType transport) {
        return new McpServerConfig(server.endpoint(), transport, new NoAuthStrategy());
    }

    private void assertNegotiated(TransportType transport, String version) {
        McpScannerSession session = manager.scannerSession();
        assertThat(manager.isConnected()).isTrue();
        assertThat(session.transportType()).isEqualTo(transport);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(version);
    }

    private void assertDiscovered(ConnectResult result, String version) {
        assertThat(result.tools()).extracting(McpToolDefinition::name).containsExactly(McpFixtureServer.toolName());
        assertThat(result.serverMetadata().serverInfo()).containsEntry("name", McpFixtureServer.serverName());
        assertThat(server.callsTo("tools/list")).singleElement()
                .extracting(McpFixtureServer.ObservedCall::protocolVersion).isEqualTo(version);
    }
}
