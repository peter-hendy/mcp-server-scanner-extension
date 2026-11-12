package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class InterpreterBreakoutTest {

    @Test
    void closesTheQuoteAndConcatenatesTheDecoderAfterTheOriginalValue() {
        assertThat(InterpreterBreakout.PYTHON_SINGLE_QUOTE.payload("Ada", "mcp1"))
                .isEqualTo("Ada'+__import__('builtins').bytes([109,99,112,49]).decode()+'");
        assertThat(InterpreterBreakout.JAVASCRIPT_DOUBLE_QUOTE.payload("Ada", "mcp1"))
                .isEqualTo("Ada\"+String.fromCharCode(109,99,112,49)+\"");
        assertThat(InterpreterBreakout.PHP_SINGLE_QUOTE.payload("Ada", "mcp1"))
                .isEqualTo("Ada'.chr(109).chr(99).chr(112).chr(49).'");
    }

    @Test
    void interpolatesIntoATemplateLiteralAfterTheOriginalValue() {
        assertThat(InterpreterBreakout.JAVASCRIPT_TEMPLATE.payload("Ada", "mcp1"))
                .isEqualTo("Ada${String.fromCharCode(109,99,112,49)}");
    }

    @ParameterizedTest
    @EnumSource(InterpreterBreakout.class)
    void everyPayloadKeepsTheOriginalValueAndNeverContainsTheDecodedValue(InterpreterBreakout breakout) {
        String payload = breakout.payload("Ada", "mcp1111111110");

        assertThat(payload).startsWith("Ada").doesNotContain("mcp1111111110");
        assertThat(breakout.label()).isNotBlank();
    }
}
