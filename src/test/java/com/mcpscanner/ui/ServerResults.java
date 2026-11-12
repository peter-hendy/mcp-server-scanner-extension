package com.mcpscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.IconDescriptor;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.ServerMetadata;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

final class ServerResults {

    static final String V2024_11_05 = """
            {"protocolVersion":"2024-11-05",
             "capabilities":{"tools":{"listChanged":true},"resources":{"subscribe":true,"listChanged":false},
                             "prompts":{"listChanged":false},"logging":{},"experimental":{"acme.bulkSearch":{}}},
             "serverInfo":{"name":"legacy-server","version":"0.9.0"}}""";

    static final String V2025_03_26 = """
            {"protocolVersion":"2025-03-26",
             "capabilities":{"tools":{},"completions":{},"logging":{}},
             "serverInfo":{"name":"completing-server","version":"1.4.0"},
             "instructions":"Call complete before prompts/get."}""";

    static final String V2025_06_18 = """
            {"protocolVersion":"2025-06-18",
             "capabilities":{"tools":{"listChanged":true},"prompts":{"listChanged":true}},
             "serverInfo":{"name":"acme-issues-mcp","title":"Acme Issues","version":"2.0.0"}}""";

    static final String V2025_11_25 = """
            {"protocolVersion":"2025-11-25",
             "capabilities":{"tools":{"listChanged":true},"resources":{"subscribe":true},"prompts":{},
                             "logging":{},"completions":{},
                             "tasks":{"list":{},"cancel":{},"requests":{"tools":{"call":{}}}},
                             "experimental":{"acme.bulkSearch":{}},"x-future":{"mode":"fast"}},
             "serverInfo":{"name":"acme-issues-mcp","title":"Acme Issues","version":"2.3.1",
                           "description":"Search and triage issues across Acme repositories.",
                           "websiteUrl":"https://example.com/acme-issues",
                           "icons":[{"src":"%s","mimeType":"image/png","sizes":["16x16"]},
                                    {"src":"https://cdn.example.com/acme.svg","mimeType":"image/svg+xml"}]},
             "instructions":"Use search_issues before get_record."}""";

    static final String MODERN_DISCOVER = """
            {"supportedVersions":["2026-07-28"],
             "capabilities":{"tools":{"listChanged":true}},
             "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"modern-server","version":"3.0.0"}}}""";

    private static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";

    private ServerResults() {
    }

    static ServerMetadata metadataOf(String resultJson) {
        JsonNode result = parse(resultJson);
        JsonNode implementation = result.path("_meta").has(SERVER_INFO_META)
                ? result.path("_meta").path(SERVER_INFO_META) : result.path("serverInfo");
        return new ServerMetadata(textFields(implementation), result.path("instructions").asText(""),
                McpObjectMapper.INSTANCE.convertValue(result.path("capabilities"), new TypeReference<>() { }),
                icons(implementation.path("icons")), result);
    }

    static String withDataPngIcon(String resultJson) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return resultJson.formatted("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    static JsonNode parse(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Map<String, String> textFields(JsonNode implementation) {
        Map<String, String> fields = new LinkedHashMap<>();
        implementation.properties().stream().filter(entry -> entry.getValue().isTextual())
                .forEach(entry -> fields.put(entry.getKey(), entry.getValue().asText()));
        return fields;
    }

    private static List<IconDescriptor> icons(JsonNode icons) {
        return StreamSupport.stream(icons.spliterator(), false)
                .map(icon -> new IconDescriptor(icon.path("src").asText(), icon.path("mimeType").asText(null),
                        StreamSupport.stream(icon.path("sizes").spliterator(), false).map(JsonNode::asText).toList()))
                .toList();
    }
}
