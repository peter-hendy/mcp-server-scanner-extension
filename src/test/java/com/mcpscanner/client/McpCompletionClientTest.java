package com.mcpscanner.client;

import com.mcpscanner.core.protocol.McpResponses.Reply;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpCompletionClientTest {

    private static final CompletionRequest REQUEST =
            new CompletionRequest(new CompletionReference.Prompt("code_review"), "language", "py");

    private final McpScannerSession session = mock(McpScannerSession.class);
    private final McpCompletionClient client = new McpCompletionClient(session);

    @Test
    void sendsCompletionCompleteWithTheRequestParamsAndReadsValuesTotalAndHasMore() {
        when(session.call("completion/complete", REQUEST.paramsJson()))
                .thenReturn(reply(Map.of("values", List.of("python", "pytorch"), "total", 10, "hasMore", true)));

        assertThat(client.complete(REQUEST))
                .isEqualTo(new CompletionOutcome.Values(List.of("python", "pytorch"), 10, true));
    }

    @Test
    void totalAndHasMoreAreAbsentWhenTheServerOmitsThem() {
        stubReply(reply(Map.of("values", List.of("a"))));

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Values(List.of("a"), null, null));
    }

    @Test
    void keepsAtMostOneHundredValues() {
        stubReply(reply(Map.of("values", IntStream.range(0, 5000).mapToObj(Integer::toString).toList(),
                "total", 5000, "hasMore", false)));

        CompletionOutcome.Values outcome = (CompletionOutcome.Values) client.complete(REQUEST);

        assertThat(outcome.values()).hasSize(100).startsWith("0").endsWith("99");
        assertThat(outcome.total()).isEqualTo(5000);
        assertThat(outcome.hasMore()).isFalse();
    }

    @Test
    void ignoresValuesThatAreNotStrings() {
        stubReply(reply(Map.of("values", Arrays.asList(1, "b", null, Map.of("x", 1), List.of("c"), true))));

        assertThat(((CompletionOutcome.Values) client.complete(REQUEST)).values()).containsExactly("b");
    }

    @Test
    void shortensAnOverlongValue() {
        stubReply(reply(Map.of("values", List.of("x".repeat(100_000)))));

        String value = ((CompletionOutcome.Values) client.complete(REQUEST)).values().get(0);

        assertThat(value).hasSize(CompletionOutcome.MAX_VALUE_LENGTH + 1).endsWith("…");
    }

    @Test
    void ignoresATotalOrHasMoreOfTheWrongType() {
        stubReply(reply(Map.of("values", List.of("a"), "total", "lots", "hasMore", "yes")));

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Values(List.of("a"), null, null));
    }

    @Test
    void ignoresANegativeOrFractionalTotal() {
        stubReply(reply(Map.of("values", List.of("a"), "total", -3)));
        assertThat(((CompletionOutcome.Values) client.complete(REQUEST)).total()).isNull();

        stubReply(reply(Map.of("values", List.of("a"), "total", 2.5)));
        assertThat(((CompletionOutcome.Values) client.complete(REQUEST)).total()).isNull();
    }

    @Test
    void aJsonRpcErrorFailsWithItsCodeAndMessage() {
        stubReply(new Reply(2, Map.of(), Map.of("code", -32602, "message", "Unknown prompt")));

        assertThat(client.complete(REQUEST))
                .isEqualTo(new CompletionOutcome.Failed("JSON-RPC error -32602: Unknown prompt"));
    }

    @Test
    void aJsonRpcErrorWithoutCodeOrMessageStillFails() {
        stubReply(new Reply(2, Map.of(), Map.of("data", List.of())));

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("JSON-RPC error"));
    }

    @Test
    void anHttpErrorFailsWithItsStatus() {
        when(session.call(eq("completion/complete"), anyString()))
                .thenThrow(new McpHttpStatusException("completion/complete", 500));

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("HTTP 500"));
    }

    @Test
    void aTransportFailureFailsWithItsReason() {
        when(session.call(eq("completion/complete"), anyString()))
                .thenThrow(new IllegalStateException("Connection reset"));

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("Connection reset"));
    }

    @Test
    void aTransportFailureWithoutMessageFailsWithItsKind() {
        when(session.call(eq("completion/complete"), anyString())).thenThrow(new IllegalStateException());

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("IllegalStateException"));
    }

    @Test
    void aReplyWithoutCompletionValuesIsInvalid() {
        stubReply(new Reply(2, Map.of("completion", Map.of()), Map.of()));
        assertThat(client.complete(REQUEST))
                .isEqualTo(new CompletionOutcome.Failed("Invalid reply: no completion values"));

        stubReply(new Reply(2, Map.of("completion", "values"), Map.of()));
        assertThat(client.complete(REQUEST))
                .isEqualTo(new CompletionOutcome.Failed("Invalid reply: no completion values"));

        stubReply(new Reply(2, nullResult(), Map.of()));
        assertThat(client.complete(REQUEST))
                .isEqualTo(new CompletionOutcome.Failed("Invalid reply: no completion values"));
    }

    @Test
    void noReplyIsInvalid() {
        stubReply(null);

        assertThat(client.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("Invalid reply: no reply"));
    }

    private void stubReply(Reply reply) {
        when(session.call(eq("completion/complete"), anyString())).thenReturn(reply);
    }

    private static Reply reply(Map<String, Object> completion) {
        return new Reply(2, Map.of("completion", completion), Map.of());
    }

    private static Map<String, Object> nullResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("completion", null);
        return result;
    }
}
