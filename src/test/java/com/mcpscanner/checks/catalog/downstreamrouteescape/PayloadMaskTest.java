package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadMaskTest {

    private final PayloadMask mask = new PayloadMask();

    @Test
    void echoedTraversalAndControlValuesMaskToTheSameText() {
        assertThat(mask.mask("You sent ../../", "../../")).isEqualTo(mask.mask("You sent .../.../", ".../.../"));
    }

    @Test
    void normalisedPathOutputMasksToTheSameTextAcrossDotCounts() {
        assertThat(mask.mask("{\"normalized\":\"..\"}", "../")).isEqualTo(mask.mask("{\"normalized\":\"...\"}", ".../"));
    }

    @Test
    void percentEncodedEchoIsMasked() {
        assertThat(mask.mask("no product ..%2F%2E", "../")).isEqualTo(mask.mask("no product ...%2f", ".../"));
    }

    @Test
    void echoedSuffixMarkerIsMaskedWithItsPayload() {
        assertThat(mask.mask("missing /api/../?mcp_route=1", "../?mcp_route=1"))
                .isEqualTo(mask.mask("missing /api/.../?mcp_route=1", ".../?mcp_route=1"));
    }

    @Test
    void distinctWordsSurviveMasking() {
        assertThat(mask.mask("{\"service\":\"index\"}", "../")).isNotEqualTo(mask.mask("{\"error\":\"not found\"}", ".../"));
    }

    @Test
    void blankPayloadOnlyMasksPathPunctuation() {
        assertThat(mask.mask("a/b.c", "")).isEqualTo(mask.mask("a.b/c", ""));
    }
}
