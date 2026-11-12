package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingHeaderVariantsTest {

    private final RoutingHeaderVariants generator = new RoutingHeaderVariants(Seeds.CANARY);
    private final HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp?tenant=a");

    @Test
    void coversEveryRoutingClientMiddlewareAndProtocolHeader() {
        assertThat(generator.variantsFor(seed)).extracting(AuthVariant::name).containsExactly(
                "routing.x-original-url", "routing.x-original-uri", "routing.x-rewrite-url",
                "routing.x-forwarded-uri", "routing.x-forwarded-prefix",
                "forwarded-for", "forwarded", "client.x-real-ip", "client.client-ip",
                "middleware", "middleware-src",
                "proto.x-forwarded-proto", "proto.x-forwarded-proto-alias");
        assertThat(generator.variantsFor(seed)).allSatisfy(variant -> {
            assertThat(variant.family()).isEqualTo(VariantFamily.ROUTING_HEADER);
            assertThat(variant.control()).isPresent();
        });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "routing.x-original-url|X-Original-URL|/mcp?tenant=a|/mcp/__mcp_c4n4ry?tenant=a",
            "routing.x-original-uri|X-Original-URI|/mcp?tenant=a|/mcp/__mcp_c4n4ry?tenant=a",
            "routing.x-rewrite-url|X-Rewrite-URL|/mcp?tenant=a|/mcp/__mcp_c4n4ry?tenant=a",
            "routing.x-forwarded-uri|X-Forwarded-Uri|/|/mcp/__mcp_c4n4ry?tenant=a",
            "routing.x-forwarded-prefix|X-Forwarded-Prefix|/|/mcp/__mcp_c4n4ry?tenant=a",
            "forwarded-for|X-Forwarded-For|127.0.0.1|203.0.113.7",
            "forwarded|Forwarded|for=127.0.0.1|for=203.0.113.7",
            "client.x-real-ip|X-Real-IP|127.0.0.1|203.0.113.7",
            "client.client-ip|Client-IP|127.0.0.1|203.0.113.7",
            "middleware|X-Middleware-Subrequest|middleware:middleware:middleware:middleware:middleware"
                    + "|neutral:neutral:neutral:neutral:neutral",
            "middleware-src|X-Middleware-Subrequest"
                    + "|src/middleware:src/middleware:src/middleware:src/middleware:src/middleware"
                    + "|neutral/middleware:neutral/middleware:neutral/middleware:neutral/middleware:neutral/middleware",
            "proto.x-forwarded-proto|X-Forwarded-Proto|https|ftp",
            "proto.x-forwarded-proto-alias|X_Forwarded_Proto|https|ftp"
    })
    void addsTheCandidateAndControlHeaderValues(String name, String header, String candidate, String control) {
        AuthVariant variant = named(generator.variantsFor(seed), name);

        assertThat(variant.candidate().applyTo(seed).headerValue(header)).isEqualTo(candidate);
        assertThat(variant.control().orElseThrow().applyTo(seed).headerValue(header)).isEqualTo(control);
        assertThat(variant.candidate().applyTo(seed).path()).isEqualTo(seed.path());
    }

    @Test
    void skipsPathHeadersWhosePathIsNotSafePrintableAscii() {
        HttpRequest unsafe = Seeds.authenticated("https://api.example.com/mcp").withPath("/m\\cp");

        assertThat(generator.variantsFor(unsafe)).extracting(AuthVariant::name)
                .doesNotContain("routing.x-original-url", "routing.x-original-uri", "routing.x-rewrite-url")
                .contains("forwarded-for", "middleware");
    }

    private static AuthVariant named(List<AuthVariant> variants, String name) {
        return variants.stream().filter(variant -> variant.name().equals(name)).findFirst().orElseThrow();
    }
}
