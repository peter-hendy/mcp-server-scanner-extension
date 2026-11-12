package com.mcpscanner.proxy;

import com.mcpscanner.logging.McpEventLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcIdRewriterTest {

    private AtomicLong counter;
    private JsonRpcIdRewriter rewriter;

    @BeforeEach
    void setUp() {
        counter = new AtomicLong(1000);
        LongSupplier idAllocator = counter::incrementAndGet;
        rewriter = new JsonRpcIdRewriter(idAllocator, McpEventLog.noop());
    }

    @Test
    void rewritesIdInSingleObjectBody() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

        String rewritten = rewriter.rewrite(body);

        JsonNode node = McpObjectMapper.INSTANCE.readTree(rewritten);
        assertThat(node.get("id").asLong()).isEqualTo(1001L);
        assertThat(node.get("method").asText()).isEqualTo("tools/list");
    }

    @Test
    void leavesObjectWithoutIdUnchanged() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

        String rewritten = rewriter.rewrite(body);

        assertThat(rewritten).isEqualTo(body);
    }

    @Test
    void rewritesIdsInBatchArrayBody() throws Exception {
        String body = "[" +
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}," +
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}," +
                "{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"tools/call\"}" +
                "]";

        String rewritten = rewriter.rewrite(body);

        JsonNode array = McpObjectMapper.INSTANCE.readTree(rewritten);
        assertThat(array.isArray()).isTrue();
        assertThat(array).hasSize(3);
        assertThat(array.get(0).get("id").asLong()).isEqualTo(1001L);
        assertThat(array.get(1).has("id")).isFalse();
        assertThat(array.get(2).get("id").asLong()).isEqualTo(1002L);
    }

    @Test
    void concurrentStringAndNumericRequestsReceiveDistinctIds() {
        java.util.Set<Long> ids = java.util.stream.IntStream.range(0, 100).parallel().mapToObj(index -> {
            String id = index % 2 == 0 ? "\"client-id\"" : "42";
            String rewritten = rewriter.rewrite("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/list\"}");
            try {
                return McpObjectMapper.INSTANCE.readTree(rewritten).get("id").asLong();
            } catch (java.io.IOException failure) {
                throw new AssertionError(failure);
            }
        }).collect(java.util.stream.Collectors.toSet());
        assertThat(ids).hasSize(100).allMatch(id -> id >= 1001 && id <= 1100);
    }

    @Test
    void leavesEmptyBodyUnchanged() {
        assertThat(rewriter.rewrite("")).isEqualTo("");
        assertThat(rewriter.rewrite(null)).isNull();
    }

    @Test
    void leavesNonJsonBodyUnchanged() {
        String body = "not-json";

        String rewritten = rewriter.rewrite(body);

        assertThat(rewritten).isEqualTo(body);
    }

    @Test
    void logsAMalformedBodyAsOneDebugLineWithoutParserLocation() {
        McpEventLog eventLog = new McpEventLog(null);
        JsonRpcIdRewriter logged = new JsonRpcIdRewriter(counter::incrementAndGet, eventLog);
        String truncated = "{\"jsonrpc\":\"2.0\",\"id\":1,\"meth";

        assertThat(logged.rewrite("/etc/passwd")).isEqualTo("/etc/passwd");
        assertThat(logged.rewrite(truncated)).isEqualTo(truncated);

        assertThat(eventLog.snapshot())
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.level()).isEqualTo(McpEventLog.Level.DEBUG);
                    assertThat(entry.message())
                            .startsWith("Forwarded malformed JSON-RPC body unchanged: ")
                            .doesNotContain("\n", "[Source");
                });
        eventLog.shutdown();
    }

    private static final String REWRITTEN = "{\"jsonrpc\":\"2.0\",\"id\":1001,\"method\":\"tools/list\"}";
    private static final String SCANNER = "{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"tools/list\"}";

    @Test
    void restoresTheScannersIdOnTheReplyToTheRewrittenRequest() {
        String reply = rewriter.restoreId("{\"jsonrpc\":\"2.0\",\"id\":1001,\"result\":{}}", REWRITTEN, SCANNER);

        assertThat(reply).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"result\":{}}");
    }

    @Test
    void leavesEveryOtherByteOfTheReplyAsTheServerSentIt() {
        String reply = "{\"result\":{\"text\":\"\\u003cscript\\u003e\",\"n\":1E2,\"a\":1,\"a\":2},  \"id\" : 1001 ,\"jsonrpc\":\"2.0\"}";

        assertThat(rewriter.restoreId(reply, REWRITTEN, SCANNER))
                .isEqualTo(reply.replace("1001", "\"abc\""));
    }

    @Test
    void readsTheLastOfDuplicateTopLevelIdsAsJacksonDoes() {
        String scanner = "{\"id\":\"a\",\"id\":\"b\",\"method\":\"tools/list\"}";

        assertThat(rewriter.restoreId("{\"id\":1001,\"result\":{}}", REWRITTEN, scanner))
                .isEqualTo("{\"id\":\"b\",\"result\":{}}");
    }

    @Test
    void leavesAReplyThatEchoesAnotherIdUnchanged() {
        String reply = "{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{}}";

        assertThat(rewriter.restoreId(reply, REWRITTEN, SCANNER)).isEqualTo(reply);
    }

    @Test
    void leavesRepliesUnchangedWhenTheyCannotCarryTheRequestId() {
        assertThat(rewriter.restoreId("", REWRITTEN, SCANNER)).isEmpty();
        assertThat(rewriter.restoreId(null, REWRITTEN, SCANNER)).isNull();
        assertThat(rewriter.restoreId("not json", REWRITTEN, SCANNER)).isEqualTo("not json");
        assertThat(rewriter.restoreId("{\"error\":\"x\"}", REWRITTEN, SCANNER)).isEqualTo("{\"error\":\"x\"}");
        assertThat(rewriter.restoreId("[{\"id\":1001}]", REWRITTEN, SCANNER)).isEqualTo("[{\"id\":1001}]");
        assertThat(rewriter.restoreId("{\"id\":null}", REWRITTEN, SCANNER)).isEqualTo("{\"id\":null}");
        assertThat(rewriter.restoreId("{\"id\":1001}", REWRITTEN, "{\"method\":\"x\"}")).isEqualTo("{\"id\":1001}");
        assertThat(rewriter.restoreId("{\"id\":1001}", REWRITTEN, "broken")).isEqualTo("{\"id\":1001}");
    }
}
