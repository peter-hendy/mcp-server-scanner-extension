package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SelectableProtocolTransportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ScriptedProtocolTransport burp = ScriptedProtocolTransport.fakeServer();
    private final ScriptedProtocolTransport jdk = ScriptedProtocolTransport.fakeServer();
    private final AtomicBoolean useBurpNetworking = new AtomicBoolean();
    private final McpEventLog eventLog = new McpEventLog(null);
    private final SelectableProtocolTransport transport =
            new SelectableProtocolTransport(useBurpNetworking::get, burp, jdk, eventLog);

    @Test
    void sendsThroughBurpWhenBurpNetworkingIsOn() throws Exception {
        useBurpNetworking.set(true);

        transport.send(toolsList(), TIMEOUT).close();

        assertThat(burp.methods()).containsExactly("tools/list");
        assertThat(jdk.requests()).isEmpty();
    }

    @Test
    void sendsThroughTheJdkClientWhenBurpNetworkingIsOff() throws Exception {
        useBurpNetworking.set(false);

        transport.send(toolsList(), TIMEOUT).close();

        assertThat(jdk.methods()).containsExactly("tools/list");
        assertThat(burp.requests()).isEmpty();
    }

    @Test
    void rereadsTheSettingOnEverySend() throws Exception {
        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();

        useBurpNetworking.set(false);
        transport.send(toolsList(), TIMEOUT).close();

        assertThat(burp.requests()).hasSize(1);
        assertThat(jdk.requests()).hasSize(1);
    }

    @Test
    void logsOnceWhenBurpNetworkingIsTurnedOffBetweenSends() throws Exception {
        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();

        useBurpNetworking.set(false);
        transport.send(toolsList(), TIMEOUT).close();
        transport.send(toolsList(), TIMEOUT).close();

        assertThat(messagesLogged()).containsExactly(SelectableProtocolTransport.BURP_NETWORKING_TURNED_OFF);
    }

    @Test
    void logsOnceWhenBurpNetworkingIsTurnedOnBetweenSends() throws Exception {
        useBurpNetworking.set(false);
        transport.send(toolsList(), TIMEOUT).close();

        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();
        transport.send(toolsList(), TIMEOUT).close();

        assertThat(messagesLogged()).containsExactly(SelectableProtocolTransport.BURP_NETWORKING_TURNED_ON);
    }

    @Test
    void logsEachFlipOnce() throws Exception {
        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();
        useBurpNetworking.set(false);
        transport.send(toolsList(), TIMEOUT).close();
        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();

        assertThat(messagesLogged()).containsExactly(SelectableProtocolTransport.BURP_NETWORKING_TURNED_OFF,
                SelectableProtocolTransport.BURP_NETWORKING_TURNED_ON);
    }

    @Test
    void logsNothingWhileTheChoiceIsUnchanged() throws Exception {
        useBurpNetworking.set(true);
        transport.send(toolsList(), TIMEOUT).close();
        transport.send(toolsList(), TIMEOUT).close();

        assertThat(messagesLogged()).isEmpty();
    }

    @Test
    void turnedOffMessageSaysNoBurpClientCertificateIsPresented() {
        assertThat(SelectableProtocolTransport.BURP_NETWORKING_TURNED_OFF)
                .contains("JDK client")
                .contains("no Burp client TLS certificate");
        assertThat(SelectableProtocolTransport.BURP_NETWORKING_TURNED_ON).contains("Burp's networking");
    }

    private List<String> messagesLogged() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private static ProtocolHttpRequest toolsList() {
        return new ProtocolHttpRequest(URI.create("https://mcp.example.test/mcp"), "POST",
                Map.of("Content-Type", "application/json"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    }
}
