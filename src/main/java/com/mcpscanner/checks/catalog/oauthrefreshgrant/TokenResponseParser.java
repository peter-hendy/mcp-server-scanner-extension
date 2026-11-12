package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.grant.IssuedTokens;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class TokenResponseParser {

    private static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);

    RefreshExchange parse(ClientAuthentication authentication, HttpRequestResponse exchange) {
        Optional<JsonNode> body = successBody(exchange);
        Optional<IssuedTokens> tokens = body.flatMap(TokenResponseParser::tokens);
        Optional<Set<String>> scope = tokens.flatMap(issued -> grantedScope(body.get(), issued.accessToken()));
        return new RefreshExchange(authentication, exchange, tokens, scope);
    }

    private static Optional<JsonNode> successBody(HttpRequestResponse exchange) {
        if (exchange == null || exchange.response() == null || exchange.response().statusCode() / 100 != 2) {
            return Optional.empty();
        }
        return readJson(exchange.response().bodyToString()).filter(JsonNode::isObject);
    }

    private static Optional<IssuedTokens> tokens(JsonNode body) {
        Optional<String> accessToken = text(body, "access_token");
        return accessToken.map(token -> new IssuedTokens(token, text(body, "refresh_token"), lifetime(body)));
    }

    private static Duration lifetime(JsonNode body) {
        JsonNode expiresIn = body.get("expires_in");
        return expiresIn != null && expiresIn.canConvertToLong() && expiresIn.asLong() > 0
                ? Duration.ofSeconds(expiresIn.asLong()) : DEFAULT_LIFETIME;
    }

    private static Optional<Set<String>> grantedScope(JsonNode body, String accessToken) {
        Optional<Set<String>> declared = text(body, "scope").map(TokenResponseParser::split);
        return declared.isPresent() ? declared : jwtScope(accessToken);
    }

    private static Optional<Set<String>> jwtScope(String accessToken) {
        String[] parts = accessToken.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return readJson(payload).flatMap(TokenResponseParser::claimScope);
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    private static Optional<Set<String>> claimScope(JsonNode claims) {
        Optional<Set<String>> scope = text(claims, "scope").map(TokenResponseParser::split);
        if (scope.isPresent()) {
            return scope;
        }
        JsonNode scp = claims.get("scp");
        if (scp != null && scp.isArray()) {
            Set<String> values = new LinkedHashSet<>();
            scp.forEach(value -> values.add(value.asText()));
            return Optional.of(values);
        }
        return text(claims, "scp").map(TokenResponseParser::split);
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? Optional.of(value.asText()) : Optional.empty();
    }

    private static Set<String> split(String scope) {
        Set<String> values = new LinkedHashSet<>(Arrays.asList(scope.trim().split("\\s+")));
        values.remove("");
        return values;
    }

    private static Optional<JsonNode> readJson(String text) {
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(text));
        } catch (IOException malformed) {
            return Optional.empty();
        }
    }
}
