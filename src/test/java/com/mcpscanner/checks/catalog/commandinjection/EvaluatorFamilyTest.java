package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluatorFamilyTest {

    private static final String VALUE = "mcp1";

    @Test
    void buildsEachInterpreterDecoderFromCharacterCodes() {
        assertThat(EvaluatorFamily.PYTHON_EXPRESSION.payload(VALUE))
                .isEqualTo("__import__('builtins').bytes([109,99,112,49]).decode()");
        assertThat(EvaluatorFamily.JAVASCRIPT_EXPRESSION.payload(VALUE)).isEqualTo("String.fromCharCode(109,99,112,49)");
        assertThat(EvaluatorFamily.RUBY_EXPRESSION.payload(VALUE)).isEqualTo("[109,99,112,49].pack('C*')");
        assertThat(EvaluatorFamily.PHP_EXPRESSION.payload(VALUE)).isEqualTo("return chr(109).chr(99).chr(112).chr(49);");
    }

    @Test
    void wrapsStatementsForEachInterpreter() {
        assertThat(EvaluatorFamily.PYTHON_STATEMENT.payload(VALUE))
                .isEqualTo("print(__import__('builtins').bytes([109,99,112,49]).decode())");
        assertThat(EvaluatorFamily.JAVASCRIPT_STATEMENT.payload(VALUE))
                .isEqualTo("console.log(String.fromCharCode(109,99,112,49));");
        assertThat(EvaluatorFamily.RUBY_STATEMENT.payload(VALUE)).isEqualTo("puts [109,99,112,49].pack('C*')");
        assertThat(EvaluatorFamily.PHP_STATEMENT.payload(VALUE)).isEqualTo("echo chr(109).chr(99).chr(112).chr(49);");
        assertThat(EvaluatorFamily.JAVASCRIPT_TEMPLATE.payload(VALUE)).isEqualTo("${String.fromCharCode(109,99,112,49)}");
    }

    @Test
    void theLiteralControlQuotesThePayloadInsideTheSameForm() {
        assertThat(EvaluatorFamily.PYTHON_EXPRESSION.literalControl("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(EvaluatorFamily.PHP_EXPRESSION.literalControl("return x;")).isEqualTo("return \"return x;\";");
        assertThat(EvaluatorFamily.PYTHON_STATEMENT.literalControl("print(x)")).isEqualTo("print(\"print(x)\")");
        assertThat(EvaluatorFamily.RUBY_STATEMENT.literalControl("puts x")).isEqualTo("puts \"puts x\"");
    }

    @Test
    void theTemplateLiteralControlEscapesTheInterpolation() {
        assertThat(EvaluatorFamily.JAVASCRIPT_TEMPLATE.literalControl("${x}")).isEqualTo("\\${x}");
    }

    @ParameterizedTest
    @EnumSource(EvaluatorFamily.class)
    void noPayloadContainsTheValueItDecodes(EvaluatorFamily family) {
        String payload = family.payload("mcp1111111110");

        assertThat(payload).doesNotContain("mcp1111111110");
        assertThat(family.literalControl(payload)).doesNotContain("mcp1111111110");
        assertThat(family.label()).isNotBlank();
    }
}
