package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.catalog.commandinjection.SimulatedToolServer.ToolReply;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class GenericEvaluatorProbeTest {

    private static final ArithmeticCanary CANARY = new ArithmeticCanary(200_000_000L, 300_000_000L);
    private static final ArithmeticCanary FRESH = new ArithmeticCanary(400_000_000L, 500_000_000L);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void reportsCodeInjectionWhenAQuoteBreakoutAppendedToTheOriginalValueEvaluates() {
        ProbeOutcome result = probe(server("greet", "name", ToolBehaviours.pythonSingleQuotedStringEvaluator()));

        assertThat(result.finding()).hasValueSatisfying(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.INTERPRETER_COMPUTED_RESULT);
            assertThat(finding.technique()).isEqualTo("Python single-quoted string breakout");
            assertThat(finding.evidence()).hasSize(5);
        });
    }

    @Test
    void aToolThatDecodesTheBareDecoderTooIsOnlyACodeExecutionArgument() {
        ProbeOutcome result = probe(server("ask_llm", "prompt", ToolBehaviours.modelDecodingAnyPythonCharacterCodes()));

        assertThat(result.finding()).map(Finding::kind).hasValue(FindingKind.EVALUATED_ARGUMENT);
    }

    @Test
    void aBreakoutThatDoesNotEvaluateAgainWithAFreshCanaryIsNotInjection() {
        Function<String, ToolReply> splicer = ToolBehaviours.pythonSingleQuotedStringEvaluator();
        Function<String, ToolReply> once = value -> value.contains(EvaluatorFamily.PYTHON_EXPRESSION.payload(FRESH.result()))
                ? ToolReply.ok("Hello") : splicer.apply(value);

        assertThat(probe(server("greet", "name", once)).finding()).isEmpty();
    }

    @Test
    void reportsBareEvaluationAsACodeExecutionArgumentWhateverTheNames() {
        ProbeOutcome result = probe(server("evaluate", "expression", ToolBehaviours.pythonEvaluator()));

        assertThat(result.finding()).hasValueSatisfying(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.EVALUATED_ARGUMENT);
            assertThat(finding.technique()).isEqualTo("Python expression");
            assertThat(finding.evidence()).hasSize(4);
        });
    }

    @Test
    void sendsOneRequestPerBreakoutAndFamilyWhenNothingEvaluates() {
        SimulatedToolServer server = server("echo", "message", ToolBehaviours.reflecting());

        assertThat(probe(server).conclusive()).isFalse();
        assertThat(server.probeCount())
                .isEqualTo(InterpreterBreakout.values().length + EvaluatorFamily.values().length);
    }

    @Test
    void aBreakoutWhoseMatchedControlAlsoCarriesTheResultIsNotInjection() {
        Function<String, ToolReply> splicer = ToolBehaviours.pythonSingleQuotedStringEvaluator();
        Function<String, ToolReply> cached = value -> value.endsWith("mcp-control")
                ? ToolReply.ok(CANARY.result()) : splicer.apply(value);

        assertThat(probe(server("greet", "name", cached)).finding()).isEmpty();
    }

    @Test
    void requiresTheLiteralControlToReturnTheUnevaluatedPayload() {
        Function<String, ToolReply> python = ToolBehaviours.pythonEvaluator();
        Function<String, ToolReply> emptyForLiterals = value -> value.startsWith("\"") ? ToolReply.ok("") : python.apply(value);

        assertThat(probe(server("calculate", "expression", emptyForLiterals)).finding()).isEmpty();
    }

    @Test
    void requiresTheMatchedControlToLackTheResult() {
        Function<String, ToolReply> python = ToolBehaviours.pythonEvaluator();
        Function<String, ToolReply> cached = value -> value.endsWith("mcp-control")
                ? ToolReply.ok(CANARY.result()) : python.apply(value);

        assertThat(probe(server("calculate", "expression", cached)).finding()).isEmpty();
    }

    @Test
    void aReplWhoseArgumentIsNotNamedCodeIsStillOnlyACodeExecutionArgument() {
        assertThat(probe(server("python_repl", "command", ToolBehaviours.pythonEvaluator())).finding())
                .map(Finding::kind).hasValue(FindingKind.EVALUATED_ARGUMENT);
    }

    private static SimulatedToolServer server(String tool, String argument, Function<String, ToolReply> behaviour) {
        return new SimulatedToolServer(tool, argument, behaviour);
    }

    private static ProbeOutcome probe(SimulatedToolServer server) {
        HttpRequestResponse base = server.baseline();
        ProbeField field = server.probeField(base);
        return new GenericEvaluatorProbe(() -> FRESH).probe(field, field.baseline(), CANARY);
    }
}
