package com.mcpscanner.scan.field;

import com.mcpscanner.scan.UriTemplateExpansion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateExpansionRegistryTest {

    private final TemplateExpansionRegistry registry = new TemplateExpansionRegistry();

    @Test
    void returnsTheRecordedVariablesOfAnExpandedUri() {
        UriTemplateExpansion.Result expansion = UriTemplateExpansion.expand("file:///{dir}/{name}");

        registry.record(expansion);

        assertThat(registry.variablesIn("file:///dir/name")).isEqualTo(expansion.variables());
    }

    @Test
    void unknownUrisHaveNoVariables() {
        assertThat(registry.variablesIn("file:///notes.txt")).isEmpty();
        assertThat(registry.variablesIn(null)).isEmpty();
    }

    @Test
    void templatesWithoutVariablesAreNotRecorded() {
        registry.record(UriTemplateExpansion.expand("file:///fixed"));

        assertThat(registry.variablesIn("file:///fixed")).isEmpty();
    }

    @Test
    void templatesExpandingToTheSameUriKeepEveryVariable() {
        registry.record(UriTemplateExpansion.expand("file:///{ab}"));
        registry.record(UriTemplateExpansion.expand("file:///{a}{b}"));

        assertThat(registry.variablesIn("file:///ab")).extracting(UriTemplateExpansion.Variable::name)
                .containsExactly("a", "ab", "b");
    }

    @Test
    void recordingTheSameExpansionTwiceDoesNotDuplicateVariables() {
        registry.record(UriTemplateExpansion.expand("file:///{name}"));
        registry.record(UriTemplateExpansion.expand("file:///{name}"));

        assertThat(registry.variablesIn("file:///name")).hasSize(1);
    }
}
