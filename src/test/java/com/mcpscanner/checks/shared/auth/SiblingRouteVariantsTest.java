package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SiblingRouteVariantsTest {

    private final SiblingRouteVariants generator = new SiblingRouteVariants();

    @Test
    void triesTheKnownSiblingRoutesBesideARootEndpoint() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("https://api.example.com/mcp"));

        assertThat(variants).extracting(variant -> variant.candidate().value()).containsExactly(
                "/mcp_message", "/mcp/", "/messages", "/messages/", "/message", "/sse");
        assertThat(variants).extracting(AuthVariant::name).startsWith("sibling./mcp_message", "sibling./mcp/");
        assertThat(variants).allSatisfy(variant -> {
            assertThat(variant.family()).isEqualTo(VariantFamily.PATH);
            assertThat(variant.control()).isEmpty();
        });
    }

    @Test
    void triesSiblingsInTheEndpointDirectoryAndAtTheRootForANestedEndpoint() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("https://api.example.com/api/mcp"));

        assertThat(variants).extracting(variant -> variant.candidate().value()).containsExactly(
                "/api/mcp_message", "/api/mcp/", "/api/messages", "/api/messages/", "/api/message", "/api/sse",
                "/mcp_message", "/messages", "/messages/", "/message", "/sse", "/mcp", "/mcp/");
    }

    @Test
    void keepsTheQueryAndNeverRepeatsTheEndpointItself() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/messages/?session_id=abc");

        List<AuthVariant> variants = generator.variantsFor(seed);

        assertThat(variants).extracting(variant -> variant.candidate().value())
                .doesNotHaveDuplicates()
                .doesNotContain("/messages/?session_id=abc")
                .contains("/messages?session_id=abc", "/mcp?session_id=abc", "/sse?session_id=abc");
        assertThat(variants).allSatisfy(variant ->
                assertThat(variant.candidate().applyTo(seed).path()).isEqualTo(variant.candidate().value()));
    }

    @Test
    void neverChangesTheHttpMethodBodyOrHeaders() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

        assertThat(generator.variantsFor(seed)).allSatisfy(variant -> {
            HttpRequest candidate = variant.candidate().applyTo(seed);
            assertThat(candidate.method()).isEqualTo("POST");
            assertThat(candidate.bodyToString()).isEqualTo(seed.bodyToString());
            assertThat(candidate.headers()).isEqualTo(seed.headers());
        });
    }
}
