package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletionRequestTest {

    @Test
    void promptReferenceNamesThePromptAndTheArgumentWithItsCurrentValue() throws Exception {
        JsonNode params = paramsOf(new CompletionRequest(new CompletionReference.Prompt("code_review"), "language", "py"));

        assertThat(params.path("ref").path("type").asText()).isEqualTo("ref/prompt");
        assertThat(params.path("ref").path("name").asText()).isEqualTo("code_review");
        assertThat(params.path("argument").path("name").asText()).isEqualTo("language");
        assertThat(params.path("argument").path("value").asText()).isEqualTo("py");
    }

    @Test
    void resourceTemplateReferenceCarriesTheTemplateAsItsUri() throws Exception {
        JsonNode params = paramsOf(new CompletionRequest(
                new CompletionReference.ResourceTemplate("repo://{owner}/{repo}"), "owner", ""));

        assertThat(params.path("ref").path("type").asText()).isEqualTo("ref/resource");
        assertThat(params.path("ref").path("uri").asText()).isEqualTo("repo://{owner}/{repo}");
        assertThat(params.path("argument").path("value").asText()).isEmpty();
    }

    @Test
    void aMissingCurrentValueIsSentEmpty() throws Exception {
        JsonNode params = paramsOf(new CompletionRequest(new CompletionReference.Prompt("p"), "a", null));

        assertThat(params.path("argument").path("value").isTextual()).isTrue();
        assertThat(params.path("argument").path("value").asText()).isEmpty();
    }

    @Test
    void serverSuppliedNamesAreEncodedAsJsonStrings() throws Exception {
        JsonNode params = paramsOf(new CompletionRequest(
                new CompletionReference.Prompt("p\",\"x\":\"y"), "a\"b", "c\\d"));

        assertThat(params.path("ref").path("name").asText()).isEqualTo("p\",\"x\":\"y");
        assertThat(params.path("argument").path("name").asText()).isEqualTo("a\"b");
        assertThat(params.path("argument").path("value").asText()).isEqualTo("c\\d");
    }

    @Test
    void aReferenceAndArgumentNameAreRequired() {
        assertThatThrownBy(() -> new CompletionRequest(null, "a", "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompletionRequest(new CompletionReference.Prompt("p"), null, ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompletionReference.Prompt(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompletionReference.ResourceTemplate(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static JsonNode paramsOf(CompletionRequest request) throws Exception {
        return McpObjectMapper.INSTANCE.readTree(request.paramsJson());
    }
}
