package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

@Timeout(30)
class RecordedServerReplayTest {
    private final McpClientManager manager =
            new McpClientManager(McpEventLog.noop());
    private RecordedServerReplay replay;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        manager.disconnect();
        if (replay != null) {
            replay.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    void autoConnectNegotiatesWhatTheRecordedServerOffered(RecordedServer recording) throws IOException {
        ConnectResult result = connect(recording);

        McpScannerSession session = manager.scannerSession();
        assertThat(manager.isConnected()).isTrue();
        assertThat(session.transportType()).isEqualTo(recording.expectedTransport());
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(recording.expected().version());
        assertThat(negotiatedSessionId()).isEqualTo(recording.negotiatedSessionId())
                .matches(sessionId -> sessionId.isPresent() == recording.expected().sessionId());
        assertThat(result.tools()).hasSize(recording.expected().tools());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    void scannerSessionListsTheRecordedTools(RecordedServer recording) throws IOException {
        connect(recording);

        Object tools = manager.scannerSession().catalog("tools/list", null).result().get("tools");

        assertThat(tools).asInstanceOf(LIST)
                .hasSize(recording.expected().tools());
    }

    static Stream<Named<RecordedServer>> recordings() throws IOException, URISyntaxException {
        return RecordedServer.catalogue();
    }

    private ConnectResult connect(RecordedServer recording) throws IOException {
        replay = new RecordedServerReplay(recording);
        return manager.connect(new McpServerConfig(replay.endpoint(), TransportType.AUTO, new NoAuthStrategy()));
    }

    private Optional<String> negotiatedSessionId() {
        return Optional.ofNullable(manager.scannerSession().protocolHeaders().get(McpHeaders.SESSION_ID));
    }
}
