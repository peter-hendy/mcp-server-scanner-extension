package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticCanaryTest {

    private final ArithmeticCanary canary = new ArithmeticCanary(100L, 23L);

    @Test
    void theResultIsThePrefixedSum() {
        assertThat(canary.result()).isEqualTo("mcp123");
    }

    @Test
    void theCommandFormatsTheArithmeticExpansionWithPrintf() {
        assertThat(canary.command()).isEqualTo("printf mcp%s $((100+23))");
    }

    @Test
    void theQuotedCommandSingleQuotesOnlyTheExpansion() {
        assertThat(canary.quotedCommand()).isEqualTo("printf mcp%s '$((100+23))'");
    }

    @Test
    void theQuotedLiteralIsWhatPrintfPrintsAfterQuoteRemoval() {
        assertThat(canary.quotedLiteral()).isEqualTo("mcp$((100+23))");
    }

    @Test
    void noPayloadTextContainsTheResult() {
        assertThat(canary.command()).doesNotContain(canary.result());
        assertThat(canary.quotedCommand()).doesNotContain(canary.result()).doesNotContain(canary.quotedLiteral());
    }
}
