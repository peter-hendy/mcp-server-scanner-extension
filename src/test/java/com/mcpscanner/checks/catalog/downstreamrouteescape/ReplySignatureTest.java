package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplySignatureTest {

    private final ReplySignature signatures = new ReplySignature(new PayloadMask());

    @Test
    void successfulToolResultHasASuccessSignature() {
        assertThat(signatures.ofSuccess(Replies.toolText("{\"service\":\"index\"}"), "../")).isPresent();
    }

    @Test
    void toolErrorRpcErrorNonOkStatusAndMissingResponseHaveNoSuccessSignature() {
        assertThat(signatures.ofSuccess(Replies.toolError("denied"), "../")).isEmpty();
        assertThat(signatures.ofSuccess(Replies.rpcError("bad"), "../")).isEmpty();
        assertThat(signatures.ofSuccess(Replies.json(500, "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}"), "../"))
                .isEmpty();
        assertThat(signatures.ofSuccess(Replies.noResponse(), "../")).isEmpty();
        assertThat(signatures.ofSuccess(Replies.json(200, "not json"), "../")).isEmpty();
    }

    @Test
    void requestIdAndResultMetaDoNotChangeTheSignature() {
        String first = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[],\"_meta\":{\"t\":1}}}";
        String second = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[],\"_meta\":{\"t\":2}}}";
        assertThat(signatures.ofSuccess(Replies.json(200, first), "../"))
                .isEqualTo(signatures.ofSuccess(Replies.json(200, second), "../"));
    }

    @Test
    void sseFramedReplyIsReadLikeJson() {
        String sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"ok\"}],\"isError\":false}}\n\n";
        assertThat(signatures.ofSuccess(Replies.exchange(200, "text/event-stream", sse), "../"))
                .isEqualTo(signatures.ofSuccess(Replies.toolText("ok"), "../"));
    }

    @Test
    void anySignatureDistinguishesErrorsFromSuccess() {
        assertThat(signatures.of(Replies.toolError("not found"), "...")).isNotEqualTo(signatures.of(
                Replies.toolText("not found"), "..."));
        assertThat(signatures.of(Replies.noResponse(), "...")).isNotEqualTo(signatures.of(
                Replies.json(404, "gone"), "..."));
    }

    @Test
    void successfulReplyHasTheSameSignatureEitherWay() {
        assertThat(signatures.of(Replies.toolText("index"), "../"))
                .isEqualTo(signatures.ofSuccess(Replies.toolText("index"), "../").orElseThrow());
    }

    @Test
    void anySignatureMasksEchoedValues() {
        assertThat(signatures.of(Replies.toolError("no item .../"), ".../"))
                .isEqualTo(signatures.of(Replies.toolError("no item ..././"), "..././"));
    }
}
