package com.mcpscanner.auth.oauth.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.oauth.ExpectedIssuer;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MetadataParsers {

    private static final Pattern AUTH_PARAMETER = Pattern.compile(
            "(?:^|,)\\s*(?:([\\w-]+)\\s+)?([\\w-]+)\\s*=\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|([^\\s,]+))");

    private MetadataParsers() {}

    public static Optional<URI> parseResourceMetadataFromWwwAuthenticate(String headerValue) {
        return bearerParameter(headerValue, "resource_metadata").flatMap(value -> {
            try {
                return Optional.of(URI.create(value));
            } catch (IllegalArgumentException invalidUri) {
                return Optional.empty();
            }
        });
    }

    public static Optional<List<String>> parseScopesFromWwwAuthenticate(String headerValue) {
        return bearerParameter(headerValue, "scope").map(value -> value.isBlank()
                ? List.of() : Arrays.stream(value.split(" +")).distinct().toList());
    }

    private static Optional<String> bearerParameter(String headerValue, String parameter) {
        if (headerValue == null) {
            return Optional.empty();
        }
        Matcher matcher = AUTH_PARAMETER.matcher(headerValue);
        boolean bearer = false;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                bearer = "Bearer".equalsIgnoreCase(matcher.group(1));
            }
            if (bearer && parameter.equalsIgnoreCase(matcher.group(2))) {
                String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                return Optional.of(value.replaceAll("\\\\(.)", "$1"));
            }
        }
        return Optional.empty();
    }

    public static List<String> protectedResourceScopes(JsonNode metadata) {
        JsonNode scopes = metadata.get("scopes_supported");
        if (scopes == null || !scopes.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(scopes.spliterator(), false)
                .filter(JsonNode::isTextual).map(JsonNode::textValue).distinct().toList();
    }

    public static boolean matchesProtectedResource(JsonNode metadata, URI expectedResource) {
        JsonNode resource = metadata.get("resource");
        if (resource == null || !resource.isTextual() || expectedResource == null) {
            return false;
        }
        try {
            URI actual = URI.create(resource.textValue());
            return isResourceUri(actual) && isResourceUri(expectedResource)
                    && actual.getScheme().equalsIgnoreCase(expectedResource.getScheme())
                    && actual.getHost().equalsIgnoreCase(expectedResource.getHost())
                    && actual.getPort() == expectedResource.getPort()
                    && Objects.equals(actual.getRawPath(), expectedResource.getRawPath())
                    && Objects.equals(actual.getRawQuery(), expectedResource.getRawQuery());
        } catch (IllegalArgumentException invalidResource) {
            return false;
        }
    }

    private static boolean isResourceUri(URI uri) {
        return uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    public static Optional<JsonNode> parseJsonObject(String json) {
        return parseJsonObject(json, null, null);
    }

    public static Optional<JsonNode> parseJsonObject(String json, McpEventLog eventLog, String context) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = McpObjectMapper.INSTANCE.readTree(json);
            return node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (Exception e) {
            warn(eventLog, "Failed to parse JSON object"
                    + (context != null ? " from " + context : "")
                    + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<AuthorizationServerMetadata> parseAs(String json) {
        return parseAs(json, null, null);
    }

    public static Optional<AuthorizationServerMetadata> parseAs(String json, McpEventLog eventLog, String context) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AuthorizationServerMetadata.parse(json));
        } catch (Exception e) {
            warn(eventLog, "Failed to parse AS metadata"
                    + (context != null ? " from " + context : "")
                    + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<AuthorizationServerMetadata> parseValidatedAs(String json, URI issuer,
                                                                         McpEventLog eventLog, String context) {
        return parseAs(json, eventLog, context).map(new ExpectedIssuer(issuer)::requireDeclaredBy);
    }

    private static void warn(McpEventLog eventLog, String message) {
        (eventLog != null ? eventLog : McpEventLog.noop()).warn(message);
    }
}
