package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSecurityPolicyTest {

    @Test
    void eachCommaSeparatedPolicyIsParsedSeparately() {
        assertThat(ContentSecurityPolicy.parseAll(List.of("script-src 'self', img-src *", "default-src 'none'")))
                .extracting(ContentSecurityPolicy::text)
                .containsExactly("script-src 'self'", "img-src *", "default-src 'none'");
    }

    @Test
    void scriptSrcElemOverridesScriptSrcForElements() {
        ContentSecurityPolicy policy = ContentSecurityPolicy.parseAll(
                List.of("script-src 'unsafe-inline'; script-src-elem 'self'; script-src-attr 'none'")).get(0);

        assertThat(policy.scriptExecution()).isEqualTo(ScriptExecution.BLOCKED);
    }

    @Test
    void policyWithoutScriptOrDefaultDirectiveBlocksNothing() {
        assertThat(ContentSecurityPolicy.parseAll(List.of("frame-ancestors 'none'")).get(0).scriptExecution())
                .isEqualTo(ScriptExecution.NOT_BLOCKED);
    }

    @Test
    void emptyDirectiveSegmentsAreIgnored() {
        assertThat(ContentSecurityPolicy.parseAll(List.of(";; script-src 'self' ;")).get(0).scriptExecution())
                .isEqualTo(ScriptExecution.BLOCKED);
    }
}
