package com.mcpscanner.checks.shared.auth.oracle;

import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyResultsTest {

    private final ReplyResults replies = new ReplyResults();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void exposesTheResultOfASuccessfulReply() {
        assertThat(replies.successfulResult(Exchanges.toolCall(200, Exchanges.SECRET_RESULT)))
                .hasValueSatisfying(result -> assertThat(result.path("content").get(0).path("text").asText())
                        .isEqualTo("s3cr3t"));
    }

    @Test
    void exposesTheResultOfAnSseFramedReply() {
        assertThat(replies.successfulResult(Exchanges.toolCallSse("event: message\ndata: " + Exchanges.SECRET_RESULT
                + "\n\n"))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 421, 500, 502, 503})
    void hasNoResultForANonSuccessStatus(int status) {
        assertThat(replies.successfulResult(Exchanges.toolCall(status, Exchanges.SECRET_RESULT))).isEmpty();
    }

    @Test
    void hasNoResultForAJsonRpcError() {
        assertThat(replies.successfulResult(Exchanges.toolCall(200,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32000,\"message\":\"boom\"}}"))).isEmpty();
    }

    @Test
    void hasNoResultForAnUnansweredRequest() {
        assertThat(replies.successfulResult(Exchanges.unanswered())).isEmpty();
    }

    @Test
    void reportsAnAuthRefusalAsRefused() {
        assertThat(replies.refused(Exchanges.toolCall(403, "{\"error\":\"origin not allowed\"}"))).isTrue();
    }

    @Test
    void doesNotReportAServerErrorAsRefused() {
        assertThat(replies.refused(Exchanges.toolCall(500, "oops"))).isFalse();
    }
}
