package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonBodyVariants implements AuthVariantGenerator {

    private static final String METHOD = "method";
    private static final String PARAMS = "params";
    private static final String JSONRPC = "jsonrpc";
    private static final String META = "_meta";
    private static final String DECOY_METHOD = McpMethods.TOOLS_LIST;
    private static final String FALLBACK_DECOY_METHOD = McpMethods.PING;
    private static final String DECOY_JSONRPC = "\"1.0\"";
    private static final String DECOY_STRING = "\"__mcp_missing__\"";
    private static final String DECOY_OBJECT = "{\"__mcp_probe__\":\"invalid\"}";

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        return parsedEnvelope(seed.bodyToString())
                .map(this::variantsFor)
                .orElse(List.of());
    }

    private List<AuthVariant> variantsFor(JsonNode envelope) {
        JsonMembers members = JsonMembers.of(envelope);
        List<AuthVariant> variants = new ArrayList<>();
        for (String key : List.of(JSONRPC, METHOD, PARAMS)) {
            if (envelope.has(key) && hasCaseVariant(key)) {
                variants.add(body("json.case." + key, members.renamed(key, capitalised(key))));
            }
        }
        variants.addAll(duplicates(METHOD, members, decoyMethod(envelope)));
        if (envelope.has(JSONRPC)) {
            variants.addAll(duplicates(JSONRPC, members, DECOY_JSONRPC));
        }
        if (envelope.path(PARAMS).isObject()) {
            variants.addAll(duplicates(PARAMS, members, DECOY_OBJECT));
        }
        JsonNode params = envelope.get(PARAMS);
        if (params != null && params.isObject()) {
            variants.addAll(paramVariants(members, params));
        }
        return List.copyOf(variants);
    }

    private List<AuthVariant> paramVariants(JsonMembers envelope, JsonNode params) {
        JsonMembers paramMembers = JsonMembers.of(params);
        List<AuthVariant> variants = new ArrayList<>();
        params.fieldNames().forEachRemaining(key -> {
            if (META.equals(key)) {
                return;
            }
            if (hasCaseVariant(key)) {
                variants.add(paramBody("json.case." + key, envelope, paramMembers.renamed(key, capitalised(key))));
            }
            decoyFor(params.get(key)).ifPresent(decoy -> {
                variants.add(paramBody("json.duplicate." + key + ".first", envelope,
                        paramMembers.duplicated(key, decoy, true)));
                variants.add(paramBody("json.duplicate." + key + ".last", envelope,
                        paramMembers.duplicated(key, decoy, false)));
            });
        });
        return variants;
    }

    private static List<AuthVariant> duplicates(String key, JsonMembers members, String decoy) {
        return List.of(
                body("json.duplicate." + key + ".first", members.duplicated(key, decoy, true)),
                body("json.duplicate." + key + ".last", members.duplicated(key, decoy, false)));
    }

    private static AuthVariant paramBody(String name, JsonMembers envelope, JsonMembers params) {
        return body(name, envelope.replaced(PARAMS, params.render()));
    }

    private static AuthVariant body(String name, JsonMembers members) {
        String body = members.render();
        return AuthVariant.uncontrolled(VariantFamily.JSON_BODY, name,
                new RequestMutation(body, request -> request.withBody(body)));
    }

    private static String decoyMethod(JsonNode envelope) {
        String method = envelope.get(METHOD).asText();
        return JsonMembers.raw(TextNode.valueOf(DECOY_METHOD.equals(method) ? FALLBACK_DECOY_METHOD : DECOY_METHOD));
    }

    private static Optional<String> decoyFor(JsonNode value) {
        if (value.isTextual()) {
            return Optional.of(DECOY_STRING);
        }
        return value.isObject() ? Optional.of(DECOY_OBJECT) : Optional.empty();
    }

    private static Optional<JsonNode> parsedEnvelope(String body) {
        try {
            JsonNode node = McpObjectMapper.INSTANCE.readTree(body);
            return node != null && node.isObject() && node.path(METHOD).isTextual()
                    ? Optional.of(node) : Optional.empty();
        } catch (JsonProcessingException unparseable) {
            return Optional.empty();
        }
    }

    private static boolean hasCaseVariant(String key) {
        return !capitalised(key).equals(key);
    }

    private static String capitalised(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
