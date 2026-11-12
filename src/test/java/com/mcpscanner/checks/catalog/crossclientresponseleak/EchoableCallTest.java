package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EchoableCallTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void listsTheStringArgumentsOfAToolCall() {
        EchoableCall call = EchoableCall.of(LeakServers.toolCall("{\"a\":\"x\",\"n\":1,\"b\":\"y\"}").request())
                .orElseThrow();

        assertThat(call.stringArguments()).containsExactly("a", "b");
    }

    @Test
    void placesAMarkerInOneArgumentAndKeepsTheRest() {
        EchoableCall call = EchoableCall.of(LeakServers.toolCall("{\"a\":\"x\",\"b\":\"y\"}").request())
                .orElseThrow();

        assertThat(call.paramsWith("b", "m1"))
                .isEqualTo("{\"name\":\"note\",\"arguments\":{\"a\":\"x\",\"b\":\"m1\"}}");
    }

    @Test
    void leavesTheProtocolEnvelopeOfAModernCallToTheProbeSession() {
        EchoableCall call = EchoableCall.of(LeakServers.modernToolCall("{\"a\":\"x\"}").request()).orElseThrow();

        assertThat(call.paramsWith("a", "m1"))
                .doesNotContain("io.modelcontextprotocol/")
                .contains("\"progressToken\":\"" + LeakServers.PROGRESS_TOKEN + "\"");
    }

    @Test
    void acceptsOnlyToolCallsWithAStringArgument() {
        assertThat(EchoableCall.of(LeakServers.toolCall("{\"n\":1}").request())).isEmpty();
        assertThat(EchoableCall.of(HttpRequest.httpRequestFromUrl(LeakServers.URL).withMethod("POST")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))).isEmpty();
    }
}
