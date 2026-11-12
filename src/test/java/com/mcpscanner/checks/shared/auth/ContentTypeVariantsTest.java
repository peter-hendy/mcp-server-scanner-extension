package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeVariantsTest {

    @Test
    void coversJsonPlainTextCharsetAndMissingContentType() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");
        List<AuthVariant> variants = new ContentTypeVariants().variantsFor(seed);

        assertThat(variants).extracting(AuthVariant::name).containsExactly(
                "content-type.json", "content-type.text-plain", "content-type.json-charset", "content-type.missing");
        assertThat(variants).extracting(variant -> variant.candidate().applyTo(seed).headerValue("Content-Type"))
                .containsExactly("application/json", "text/plain", "application/json; charset=utf-8", null);
        assertThat(variants).allSatisfy(variant -> {
            assertThat(variant.family()).isEqualTo(VariantFamily.CONTENT_TYPE);
            assertThat(variant.control()).isEmpty();
            assertThat(variant.candidate().applyTo(seed).bodyToString()).isEqualTo(seed.bodyToString());
        });
    }
}
