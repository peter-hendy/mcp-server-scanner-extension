package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodyVariantsTest {

    private static final String PARAMS = "{\"name\":\"read_secret\",\"arguments\":{\"path\":\"/etc\"},\"_meta\":{\"k\":1}}";

    private final JsonBodyVariants generator = new JsonBodyVariants();
    private final HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

    @Test
    void coversEnvelopeAndParamKeysForAToolCall() {
        assertThat(generator.variantsFor(seed)).extracting(AuthVariant::name).containsExactly(
                "json.case.jsonrpc", "json.case.method", "json.case.params",
                "json.duplicate.method.first", "json.duplicate.method.last",
                "json.duplicate.jsonrpc.first", "json.duplicate.jsonrpc.last",
                "json.duplicate.params.first", "json.duplicate.params.last",
                "json.case.name", "json.duplicate.name.first", "json.duplicate.name.last",
                "json.case.arguments", "json.duplicate.arguments.first", "json.duplicate.arguments.last");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "json.case.jsonrpc|{\"Jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":" + PARAMS + "}",
            "json.case.method|{\"jsonrpc\":\"2.0\",\"id\":7,\"Method\":\"tools/call\",\"params\":" + PARAMS + "}",
            "json.case.params|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"Params\":" + PARAMS + "}",
            "json.duplicate.method.first|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                    + "\"method\":\"tools/list\",\"params\":" + PARAMS + "}",
            "json.duplicate.method.last|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\","
                    + "\"method\":\"tools/call\",\"params\":" + PARAMS + "}",
            "json.duplicate.jsonrpc.first|{\"jsonrpc\":\"2.0\",\"jsonrpc\":\"1.0\",\"id\":7,"
                    + "\"method\":\"tools/call\",\"params\":" + PARAMS + "}",
            "json.duplicate.jsonrpc.last|{\"jsonrpc\":\"1.0\",\"jsonrpc\":\"2.0\",\"id\":7,"
                    + "\"method\":\"tools/call\",\"params\":" + PARAMS + "}",
            "json.duplicate.params.first|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + PARAMS + ",\"params\":{\"__mcp_probe__\":\"invalid\"}}",
            "json.duplicate.params.last|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                    + "\"params\":{\"__mcp_probe__\":\"invalid\"},\"params\":" + PARAMS + "}",
            "json.case.name|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + "{\"Name\":\"read_secret\",\"arguments\":{\"path\":\"/etc\"},\"_meta\":{\"k\":1}}}",
            "json.duplicate.name.first|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"read_secret\",\"name\":\"__mcp_missing__\",\"arguments\":{\"path\":\"/etc\"},"
                    + "\"_meta\":{\"k\":1}}}",
            "json.duplicate.name.last|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"__mcp_missing__\",\"name\":\"read_secret\",\"arguments\":{\"path\":\"/etc\"},"
                    + "\"_meta\":{\"k\":1}}}",
            "json.duplicate.arguments.first|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"read_secret\",\"arguments\":{\"path\":\"/etc\"},"
                    + "\"arguments\":{\"__mcp_probe__\":\"invalid\"},\"_meta\":{\"k\":1}}}",
            "json.duplicate.arguments.last|{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"read_secret\",\"arguments\":{\"__mcp_probe__\":\"invalid\"},"
                    + "\"arguments\":{\"path\":\"/etc\"},\"_meta\":{\"k\":1}}}"
    })
    void rendersTheExactMutatedBody(String name, String body) {
        AuthVariant variant = named(generator.variantsFor(seed), name);

        assertThat(variant.candidate().value()).isEqualTo(body);
        assertThat(variant.candidate().applyTo(seed).bodyToString()).isEqualTo(body);
        assertThat(variant.family()).isEqualTo(VariantFamily.JSON_BODY);
        assertThat(variant.control()).isEmpty();
    }

    @Test
    void resourceReadVariesTheUriParam() {
        HttpRequest read = Seeds.authenticated("https://api.example.com/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":\"r1\",\"method\":\"resources/read\",\"params\":{\"uri\":\"file:///a\"}}");

        assertThat(generator.variantsFor(read)).extracting(AuthVariant::name)
                .contains("json.case.uri", "json.duplicate.uri.first", "json.duplicate.uri.last");
        assertThat(named(generator.variantsFor(read), "json.duplicate.uri.last").candidate().value())
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":\"r1\",\"method\":\"resources/read\","
                        + "\"params\":{\"uri\":\"__mcp_missing__\",\"uri\":\"file:///a\"}}");
    }

    @Test
    void toolsListUsesPingAsTheDuplicateMethodDecoy() {
        HttpRequest list = Seeds.authenticated("https://api.example.com/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");

        assertThat(named(generator.variantsFor(list), "json.duplicate.method.first").candidate().value())
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"method\":\"ping\"}");
        assertThat(generator.variantsFor(list)).extracting(AuthVariant::name).doesNotContain("json.case.params");
    }

    @Test
    void notificationsKeepHavingNoId() {
        HttpRequest notification = Seeds.authenticated("https://api.example.com/mcp",
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        assertThat(generator.variantsFor(notification)).isNotEmpty()
                .allSatisfy(variant -> assertThat(variant.candidate().value()).doesNotContain("\"id\""));
    }

    @Test
    void numericAndBooleanParamsGetOnlyACaseVariant() {
        HttpRequest call = Seeds.authenticated("https://api.example.com/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"x/y\",\"params\":{\"limit\":5,\"flag\":true}}");

        assertThat(generator.variantsFor(call)).extracting(AuthVariant::name)
                .contains("json.case.limit", "json.case.flag")
                .noneMatch(name -> name.startsWith("json.duplicate.limit") || name.startsWith("json.duplicate.flag"));
    }

    @Test
    void nonJsonRpcBodiesYieldNoVariants() {
        assertThat(generator.variantsFor(Seeds.authenticated("https://api.example.com/mcp", "not json"))).isEmpty();
        assertThat(generator.variantsFor(Seeds.authenticated("https://api.example.com/mcp", "[{\"method\":\"a\"}]")))
                .isEmpty();
        assertThat(generator.variantsFor(Seeds.authenticated("https://api.example.com/mcp", "{\"id\":1}"))).isEmpty();
    }

    private static AuthVariant named(List<AuthVariant> variants, String name) {
        return variants.stream().filter(variant -> variant.name().equals(name)).findFirst().orElseThrow();
    }
}
