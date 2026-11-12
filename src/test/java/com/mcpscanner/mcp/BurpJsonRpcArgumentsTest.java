package com.mcpscanner.mcp;

import com.mcpscanner.mcp.JsonStep.Item;
import com.mcpscanner.mcp.JsonStep.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BurpJsonRpcArgumentsTest {

    private static final String TOOL_CALL = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"fetch\",\"arguments\":{\"url\":\"u\"},\"_meta\":{\"k\":\"v\"}}}";

    @Test
    void toolAndPromptArgumentsAreFuzzedButNotTheirNameOrMetadata() {
        for (String method : List.of("tools/call", "prompts/get")) {
            BurpJsonRpcArguments arguments = of(TOOL_CALL.replace("tools/call", method)).orElseThrow();

            assertThat(arguments.fuzzesScalarAt(steps("params", "arguments", "url"))).isTrue();
            assertThat(arguments.fuzzesScalarAt(List.of(new Member("params"), new Member("arguments"),
                    new Member("tags"), new Item(0)))).isTrue();
            assertThat(arguments.fuzzesScalarAt(steps("params", "name"))).isFalse();
            assertThat(arguments.fuzzesScalarAt(steps("params", "arguments"))).isFalse();
            assertThat(arguments.fuzzesScalarAt(steps("params", "_meta", "k"))).isFalse();
        }
    }

    @Test
    void resourceReadFuzzesOnlyItsUri() {
        BurpJsonRpcArguments arguments = of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"file:///a\",\"other\":1}}").orElseThrow();

        assertThat(arguments.fuzzesScalarAt(steps("params", "uri"))).isTrue();
        assertThat(arguments.fuzzesScalarAt(steps("params", "other"))).isFalse();
    }

    @Test
    void otherMethodsFuzzEverythingUnderParamsButMetadata() {
        BurpJsonRpcArguments arguments = of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"completion/complete\","
                + "\"params\":{\"ref\":{\"name\":\"p\"},\"_meta\":{\"k\":1}}}").orElseThrow();

        assertThat(arguments.fuzzesScalarAt(steps("params", "ref", "name"))).isTrue();
        assertThat(arguments.fuzzesScalarAt(steps("params", "_meta", "k"))).isFalse();
        assertThat(arguments.fuzzesScalarAt(steps("id"))).isFalse();
        assertThat(arguments.fuzzesScalarAt(List.of())).isFalse();
    }

    @Test
    void positionalParamsAreEachAnArgumentEvenWhenNamedMeta() {
        BurpJsonRpcArguments arguments = of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"sum\","
                + "\"params\":[1,{\"_meta\":2}]}").orElseThrow();

        assertThat(arguments.fuzzesScalarAt(List.of(new Member("params"), new Item(0)))).isTrue();
        assertThat(arguments.fuzzesScalarAt(List.of(new Member("params"), new Item(1), new Member("_meta"))))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"jsonrpc\":\"1.0\",\"method\":\"ping\",\"params\":{}}",
            "{\"jsonrpc\":2.0,\"method\":\"ping\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"method\":7,\"params\":{}}",
            "{\"method\":\"ping\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"params\":{",
            "x{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"params\":{}}",
            "\"jsonrpc\"",
            "",
            "[]"})
    void bodiesBurpDoesNotRecogniseHaveNoArguments(String body) {
        assertThat(of(body)).isEmpty();
    }

    @Test
    void aSingleCallAfterLeadingWhitespaceIsRecognised() {
        assertThat(of(" \r\n\t{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}")).isPresent();
        assertThat(BurpJsonRpcArguments.in(null)).isEmpty();
    }

    @Test
    void batchesAreLeftToTheExtensionWhichOffersNoFieldsInThem() {
        assertThat(of("[{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"params\":{\"a\":1}}]")).isEmpty();
    }

    private static Optional<BurpJsonRpcArguments> of(String body) {
        return BurpJsonRpcArguments.in(body.getBytes(StandardCharsets.UTF_8));
    }

    private static List<JsonStep> steps(String... members) {
        return java.util.Arrays.stream(members).<JsonStep>map(Member::new).toList();
    }
}
