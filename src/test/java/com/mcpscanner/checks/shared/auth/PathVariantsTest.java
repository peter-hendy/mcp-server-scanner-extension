package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PathVariantsTest {

    private final PathVariants generator = new PathVariants(Seeds.CANARY);

    @Test
    void producesAllTwentyOnePathShapesInSourceOrder() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("https://api.example.com/mcp"));

        assertThat(variants).extracting(AuthVariant::name).containsExactly(
                "path.mcp-message", "path.dot-segment", "path.semicolon", "path.trailing-slash",
                "path.trailing-dot", "path.case", "path.double-encoded-path", "path.null-byte",
                "path.backslash", "path.percent-letter", "path.encoded-dot-segment", "path.parent-traversal",
                "path.encoded-slash", "path.zero-width", "path.word-joiner", "path.soft-hyphen",
                "path.unicode-space", "path.encoded-space", "path.encoded-tab", "path.encoded-crlf",
                "path.accent-fold");
        assertThat(variants).allSatisfy(variant -> {
            assertThat(variant.family()).isEqualTo(VariantFamily.PATH);
            assertThat(variant.control()).isPresent();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "path.mcp-message, /mcp_message?tenant=a, /mcp/__mcp_c4n4ry_message?tenant=a",
            "path.dot-segment, /mcp/./?tenant=a, /mcp/__mcp_c4n4ry/./?tenant=a",
            "path.semicolon, /mcp/..;/?tenant=a, /mcp/__mcp_c4n4ry/..;/?tenant=a",
            "path.trailing-slash, /mcp/?tenant=a, /mcp/__mcp_c4n4ry/?tenant=a",
            "path.trailing-dot, /mcp.?tenant=a, /mcp/__mcp_c4n4ry.?tenant=a",
            "path.case, /MCP?tenant=a, /MCP/__MCP_C4N4RY?tenant=a",
            "path.double-encoded-path, /mcp%252f?tenant=a, /mcp/__mcp_c4n4ry%252f?tenant=a",
            "path.null-byte, /mcp%00?tenant=a, /mcp/__mcp_c4n4ry%00?tenant=a",
            "path.backslash, /mcp%5c?tenant=a, /mcp/__mcp_c4n4ry%5c?tenant=a",
            "path.percent-letter, /%6Dcp?tenant=a, /%6Dcp/__mcp_c4n4ry?tenant=a",
            "path.encoded-dot-segment, /%2e/mcp?tenant=a, /%2e/mcp/__mcp_c4n4ry?tenant=a",
            "path.parent-traversal, /x/%2e%2e/mcp?tenant=a, /x/%2e%2e/mcp/__mcp_c4n4ry?tenant=a",
            "path.encoded-slash, /mcp%2f?tenant=a, /mcp/__mcp_c4n4ry%2f?tenant=a",
            "path.zero-width, /mc%E2%80%8Bp?tenant=a, /mc%E2%80%8Bp/__mcp_c4n4ry?tenant=a",
            "path.word-joiner, /mc%E2%81%A0p?tenant=a, /mc%E2%81%A0p/__mcp_c4n4ry?tenant=a",
            "path.soft-hyphen, /mc%C2%ADp?tenant=a, /mc%C2%ADp/__mcp_c4n4ry?tenant=a",
            "path.unicode-space, /mcp%E3%80%80?tenant=a, /mcp/__mcp_c4n4ry%E3%80%80?tenant=a",
            "path.encoded-space, /mcp%20?tenant=a, /mcp/__mcp_c4n4ry%20?tenant=a",
            "path.encoded-tab, /mcp%09?tenant=a, /mcp/__mcp_c4n4ry%09?tenant=a",
            "path.encoded-crlf, /mcp%0d%0a?tenant=a, /mcp/__mcp_c4n4ry%0d%0a?tenant=a",
            "path.accent-fold, /m%C3%A7p?tenant=a, /m%C3%A7p/__mcp_c4n4ry?tenant=a"
    })
    void eachShapeRewritesTheEndpointAndItsNonexistentChildControl(String name, String candidate,
                                                                    String control) {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp?tenant=a");
        AuthVariant variant = byName(generator.variantsFor(seed)).get(name);

        assertThat(variant.candidate().value()).isEqualTo(candidate);
        assertThat(variant.candidate().applyTo(seed).path()).isEqualTo(candidate);
        assertThat(variant.control().orElseThrow().value()).isEqualTo(control);
        assertThat(variant.control().orElseThrow().applyTo(seed).path()).isEqualTo(control);
    }

    @Test
    void dropsShapesThatLeaveTheTargetUnchangedOrRepeatAnEarlierShape() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("https://api.example.com/"));

        assertThat(variants).extracting(variant -> variant.candidate().value()).doesNotHaveDuplicates()
                .doesNotContain("/");
        assertThat(byName(variants)).containsKey("path.mcp-message")
                .doesNotContainKeys("path.trailing-slash", "path.case", "path.percent-letter");
        assertThat(byName(variants).get("path.mcp-message").candidate().value()).isEqualTo("/mcp_message");
    }

    @Test
    void neverChangesTheHttpMethodOrBody() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

        assertThat(generator.variantsFor(seed)).allSatisfy(variant -> {
            HttpRequest candidate = variant.candidate().applyTo(seed);
            assertThat(candidate.method()).isEqualTo("POST");
            assertThat(candidate.bodyToString()).isEqualTo(seed.bodyToString());
        });
    }

    private static Map<String, AuthVariant> byName(List<AuthVariant> variants) {
        return variants.stream().collect(Collectors.toMap(AuthVariant::name, Function.identity()));
    }
}
