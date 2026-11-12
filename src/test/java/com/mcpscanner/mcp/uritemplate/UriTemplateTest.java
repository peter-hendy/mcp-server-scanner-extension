package com.mcpscanner.mcp.uritemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class UriTemplateTest {

    @Test
    void readsEachExpressionWithItsSpanOperatorAndVariables() {
        UriTemplate template = UriTemplate.parse("repo://{owner}/{repo}/issues{?state,labels*}").orElseThrow();

        assertThat(template.expressions()).containsExactly(
                new TemplateExpression(7, 14, TemplateOperator.SIMPLE, List.of(variable("owner"))),
                new TemplateExpression(15, 21, TemplateOperator.SIMPLE, List.of(variable("repo"))),
                new TemplateExpression(28, 44, TemplateOperator.FORM_QUERY,
                        List.of(variable("state"), new TemplateVariable("labels", true, OptionalInt.empty()))));
    }

    @Test
    void readsEveryOperator() {
        UriTemplate template = UriTemplate.parse("{a}{+b}{#c}{.d}{/e}{;f}{?g}{&h}").orElseThrow();

        assertThat(template.expressions()).extracting(TemplateExpression::operator).containsExactly(
                TemplateOperator.SIMPLE, TemplateOperator.RESERVED, TemplateOperator.FRAGMENT,
                TemplateOperator.LABEL, TemplateOperator.PATH, TemplateOperator.PATH_STYLE,
                TemplateOperator.FORM_QUERY, TemplateOperator.QUERY_CONTINUATION);
    }

    @Test
    void readsAPrefixModifier() {
        UriTemplate template = UriTemplate.parse("x/{name:3}").orElseThrow();

        assertThat(template.expressions().get(0).variables())
                .containsExactly(new TemplateVariable("name", false, OptionalInt.of(3)));
    }

    @Test
    void acceptsDottedAndPercentEncodedNames() {
        assertThat(UriTemplate.parse("{a.b,c%20d}")).isPresent();
    }

    @Test
    void aTemplateWithoutExpressionsIsValidAndEmpty() {
        assertThat(UriTemplate.parse("docs://readme").orElseThrow().expressions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a{b", "a}b", "{}", "{?}", "{=x}", "{!x}", "{a{b}", "{a:0}", "{a:10000}", "{a*:3}",
            "{a,}", "{a b}", "{.a.}"})
    void rejectsMalformedTemplates(String text) {
        assertThat(UriTemplate.parse(text)).isEmpty();
    }

    @Test
    void rejectsAMissingTemplate() {
        assertThat(UriTemplate.parse(null)).isEmpty();
    }

    private static TemplateVariable variable(String name) {
        return new TemplateVariable(name, false, OptionalInt.empty());
    }
}
