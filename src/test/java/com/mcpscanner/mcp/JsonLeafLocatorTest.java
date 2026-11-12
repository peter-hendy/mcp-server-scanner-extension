package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLeafLocatorTest {

    @Test
    void locatesEveryScalarKindUnderParams() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"s\":\"text\",\"n\":-12.5e3,\"t\":true,\"f\":false,\"z\":null}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(leaves).extracting(JsonLeaf::path)
                .containsExactly("params.s", "params.n", "params.t", "params.f", "params.z");
        assertThat(leaves).extracting(JsonLeaf::kind).containsExactly(
                JsonValueKind.STRING, JsonValueKind.NUMBER, JsonValueKind.BOOLEAN,
                JsonValueKind.BOOLEAN, JsonValueKind.NULL);
        assertThat(slices(body, leaves)).containsExactly("\"text\"", "-12.5e3", "true", "false", "null");
    }

    @Test
    void ignoresEnvelopeFieldsOutsideParams() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\",\"params\":{\"a\":1}}";

        assertThat(locate(body)).extracting(JsonLeaf::path).containsExactly("params.a");
    }

    @Test
    void walksNestedObjectsAndArrayElementsWithServerStylePaths() {
        String body = "{\"method\":\"tools/call\",\"params\":{\"name\":\"x\",\"arguments\":"
                + "{\"options\":{\"tags\":[\"a\",{\"deep\":2},[true]]}},\"_meta\":{\"progressToken\":\"p\"}}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(leaves).extracting(JsonLeaf::path).containsExactly(
                "params.name",
                "params.arguments.options.tags[0]",
                "params.arguments.options.tags[1].deep",
                "params.arguments.options.tags[2][0]",
                "params._meta.progressToken");
        assertThat(slices(body, leaves)).containsExactly("\"x\"", "\"a\"", "2", "true", "\"p\"");
    }

    @Test
    void exposesEmptyContainersAsLeaves() {
        String body = "{\"method\":\"x\",\"params\":{\"o\":{},\"a\":[ ],\"inner\":{\"e\":{}}}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(leaves).extracting(JsonLeaf::path).containsExactly("params.o", "params.a", "params.inner.e");
        assertThat(leaves).extracting(JsonLeaf::kind)
                .containsExactly(JsonValueKind.OBJECT, JsonValueKind.ARRAY, JsonValueKind.OBJECT);
        assertThat(slices(body, leaves)).containsExactly("{}", "[ ]", "{}");
    }

    @Test
    void exposesEmptyParamsObjectAsSingleLeaf() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";

        assertThat(locate(body)).extracting(JsonLeaf::path).containsExactly("params");
    }

    @Test
    void walksPositionalArrayParams() {
        String body = "{\"method\":\"x\",\"params\":[1,\"two\"]}";

        assertThat(locate(body)).extracting(JsonLeaf::path).containsExactly("params[0]", "params[1]");
    }

    @Test
    void returnsNothingWhenParamsAbsent() {
        assertThat(locate("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\"}")).isEmpty();
    }

    @Test
    void returnsNothingForBatchesAndMalformedBodies() {
        assertThat(locate("[{\"method\":\"ping\",\"params\":{\"a\":1}}]")).isEmpty();
        assertThat(locate("{\"params\":{\"a\":")).isEmpty();
        assertThat(locate("not json")).isEmpty();
        assertThat(JsonLeafLocator.paramsLeaves(new byte[0])).isEmpty();
        assertThat(JsonLeafLocator.paramsLeaves(null)).isEmpty();
    }

    @Test
    void ignoresNestedParamsKeyBelowRoot() {
        String body = "{\"method\":\"x\",\"other\":{\"params\":{\"a\":1}},\"params\":{\"b\":2}}";

        assertThat(locate(body)).extracting(JsonLeaf::path).containsExactly("params.b");
    }

    @Test
    void offsetsAreUtf8ByteOffsetsAfterMultibyteText() {
        String body = "{\"method\":\"x\",\"params\":{\"city\":\"Zürich ☃ 🐍\",\"n\":3}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(slices(body, leaves)).containsExactly("\"Zürich ☃ 🐍\"", "3");
        assertThat(leaves.get(0).decodedValue()).isEqualTo("Zürich ☃ 🐍");
    }

    @Test
    void unicodeKeysAppearUnescapedInPaths() {
        String body = "{\"method\":\"x\",\"params\":{\"\\u00e9t\\u00e9\":1,\"日本\":2}}";

        assertThat(locate(body)).extracting(JsonLeaf::path).containsExactly("params.été", "params.日本");
    }

    @Test
    void stringsWithEscapesKeepRawSpanAndDecodedValue() {
        String body = "{\"method\":\"x\",\"params\":{\"q\":\"a\\\"b\\\\c\\n\\u0041\",\"after\":\"z\"}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(slices(body, leaves)).containsExactly("\"a\\\"b\\\\c\\n\\u0041\"", "\"z\"");
        assertThat(leaves.get(0).decodedValue()).isEqualTo("a\"b\\c\nA");
    }

    @Test
    void numbersOfEveryShapeSpanExactly() {
        String body = "{\"method\":\"x\",\"params\":{\"a\":0,\"b\":-1,\"c\":1.5E-7,\"d\":123456789012345678901234567890}}";

        assertThat(slices(body, locate(body)))
                .containsExactly("0", "-1", "1.5E-7", "123456789012345678901234567890");
    }

    @Test
    void toleratesWhitespaceAroundValues() {
        String body = "{ \"method\" : \"x\" ,\n \"params\" : {\n  \"a\" :  \"v\" ,\n  \"b\" : 4 \n } }";

        assertThat(slices(body, locate(body))).containsExactly("\"v\"", "4");
    }

    @Test
    void handlesHugeBodies() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 5_000; i++) {
            items.append(i == 0 ? "" : ",").append("{\"k\":").append(i).append('}');
        }
        String big = "x".repeat(2_000_000);
        String body = "{\"method\":\"x\",\"params\":{\"big\":\"" + big + "\",\"items\":[" + items + "]}}";

        List<JsonLeaf> leaves = locate(body);

        assertThat(leaves).hasSize(5_001);
        assertThat(leaves.get(0).endExclusive() - leaves.get(0).startInclusive()).isEqualTo(big.length() + 2);
        assertThat(leaves.get(5_000).path()).isEqualTo("params.items[4999].k");
        assertThat(slices(body, List.of(leaves.get(5_000)))).containsExactly("4999");
    }

    private static List<JsonLeaf> locate(String body) {
        return JsonLeafLocator.paramsLeaves(body.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> slices(String body, List<JsonLeaf> leaves) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return leaves.stream()
                .map(leaf -> new String(bytes, leaf.startInclusive(),
                        leaf.endExclusive() - leaf.startInclusive(), StandardCharsets.UTF_8))
                .toList();
    }
}
