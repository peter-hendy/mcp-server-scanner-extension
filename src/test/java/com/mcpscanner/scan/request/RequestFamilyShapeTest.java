package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ExtensionMetadata;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.scan.UriTemplateExpansion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFamilyShapeTest {

    private final List<McpMessage> messages =
            SampleSurfaces.jsonRpc(RequestGenerator.standard().scanAll(SampleSurfaces.full(), family -> true));

    @Test
    void toolCallCarriesNameSchemaDefaultArgumentsAndMeta() {
        JsonNode params = params("tools/call", 0);

        assertThat(params.path("name").asText()).isEqualTo("lookup");
        assertThat(params.path("arguments").path("city").isTextual()).isTrue();
        assertThat(params.path("arguments").path("limit").isNumber()).isTrue();
        assertThat(params.path("arguments").has("opts")).isTrue();
        assertThat(params.path("_meta").path("progressToken").asText()).isEqualTo(McpParams.PROGRESS_TOKEN);
        assertThat(message("tools/call", 0).routingName()).contains("lookup");
    }

    @Test
    void resourceAndTemplateReadsCarryConcreteUris() {
        assertThat(params("resources/read", 0).path("uri").asText()).isEqualTo("file:///notes.txt");
        assertThat(params("resources/read", 1).path("uri").asText()).isEqualTo("file:///docs/name");
        assertThat(message("resources/read", 1).family()).isEqualTo(RequestFamily.RESOURCE_TEMPLATES_READ);
        assertThat(message("resources/read", 1).routingName()).contains("file:///docs/name");
    }

    @Test
    void onlyTemplateReadsCarryTheirTemplateExpansion() {
        McpMessage templateRead = message("resources/read", 1);

        assertThat(templateRead.templateExpansion()).hasValueSatisfying(expansion -> {
            assertThat(expansion.expandedUri()).isEqualTo("file:///docs/name");
            assertThat(expansion.variables()).extracting(UriTemplateExpansion.Variable::name)
                    .containsExactly("name");
        });
        assertThat(messages).filteredOn(message -> message.templateExpansion().isPresent()).containsExactly(templateRead);
    }

    @Test
    void promptGetCarriesEveryDeclaredArgument() {
        JsonNode params = params("prompts/get", 0);

        assertThat(params.path("name").asText()).isEqualTo("greet");
        assertThat(params.path("arguments").path("who").asText()).isEqualTo("test");
        assertThat(params.path("arguments").path("tone").asText()).isEqualTo("test");
    }

    @Test
    void everyListRequestCarriesACursor() {
        for (String method : List.of("tools/list", "resources/list", "resources/templates/list", "prompts/list")) {
            assertThat(params(method, 0).path("cursor").asText()).as(method).isEqualTo(ListRequests.CURSOR);
        }
    }

    @Test
    void initializeCarriesVersionCapabilitiesAndFullClientInfo() {
        JsonNode params = params("initialize", 0);
        JsonNode clientInfo = params.path("clientInfo");

        assertThat(params.path("protocolVersion").asText()).isEqualTo(McpRequests.supportedInitializeVersions().get(0));
        assertThat(params.path("capabilities").path("roots").path("listChanged").asBoolean()).isTrue();
        assertThat(clientInfo.path("name").asText()).isEqualTo(ExtensionMetadata.NAME);
        assertThat(clientInfo.path("version").asText()).isEqualTo(ExtensionMetadata.VERSION);
        assertThat(clientInfo.has("title")).isTrue();
        assertThat(clientInfo.has("description")).isTrue();
        assertThat(clientInfo.path("websiteUrl").asText()).startsWith("https://");
        assertThat(clientInfo.path("icons").get(0).path("src").asText()).startsWith("https://");
    }

    @Test
    void notificationsAreMarkedAsNotificationsWithSpecParams() {
        assertThat(messages).filteredOn(McpMessage::notification).extracting(McpMessage::method).containsExactly(
                "notifications/initialized", "notifications/cancelled", "notifications/progress",
                "notifications/roots/list_changed");
        assertThat(params("notifications/cancelled", 0).path("requestId").asText()).isNotEmpty();
        JsonNode progress = params("notifications/progress", 0);
        assertThat(progress.path("progressToken").asText()).isEqualTo(McpParams.PROGRESS_TOKEN);
        assertThat(progress.path("progress").isNumber()).isTrue();
        assertThat(progress.path("total").isNumber()).isTrue();
        assertThat(progress.path("message").isTextual()).isTrue();
    }

    @Test
    void completionReferencesPromptsAndTemplates() {
        JsonNode prompt = params("completion/complete", 0);
        JsonNode template = params("completion/complete", 1);

        assertThat(prompt.path("ref").path("type").asText()).isEqualTo("ref/prompt");
        assertThat(prompt.path("ref").path("name").asText()).isEqualTo("greet");
        assertThat(prompt.path("argument").path("name").asText()).isEqualTo("who");
        assertThat(template.path("ref").path("type").asText()).isEqualTo("ref/resource");
        assertThat(template.path("ref").path("uri").asText()).isEqualTo("file:///docs/{name}");
        assertThat(template.path("argument").path("name").asText()).isEqualTo("name");
    }

    @Test
    void loggingPingSubscribeAndDiscoverShapes() {
        assertThat(params("logging/setLevel", 0).path("level").asText()).isEqualTo("debug");
        assertThat(params("ping", 0).has("_meta")).isTrue();
        assertThat(params("resources/subscribe", 0).path("uri").asText()).isEqualTo("file:///notes.txt");
        assertThat(params("resources/unsubscribe", 0).path("uri").asText()).isEqualTo("file:///notes.txt");
        assertThat(message("server/discover", 0).params()).isEmpty();
    }

    @Test
    void onlyToolResourceAndPromptInvocationsCarryARoutingName() {
        assertThat(messages).filteredOn(message -> message.routingName().isPresent())
                .extracting(McpMessage::method).containsOnly("tools/call", "resources/read", "prompts/get");
    }

    private McpMessage message(String method, int occurrence) {
        return messages.stream().filter(message -> message.method().equals(method)).skip(occurrence)
                .findFirst().orElseThrow();
    }

    private JsonNode params(String method, int occurrence) {
        return message(method, occurrence).params().orElseThrow();
    }
}
