package com.mcpscanner.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpRequests;

import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

public final class McpOperationFraming {

    private static final String META = "_meta";
    private static final String PROTOCOL_VERSION_META = "io.modelcontextprotocol/protocolVersion";
    private static final Set<String> PROTOCOL_ENVELOPE_KEYS = protocolEnvelopeKeys();

    public interface Framer {
        McpRequests.Request operation(String method, String paramsJson, long id);

        McpRequests.Request notification(String method, String paramsJson);
    }

    private McpOperationFraming() {}

    public static boolean carriesProtocolEnvelope(ObjectNode params) {
        return params.path(META).has(PROTOCOL_VERSION_META);
    }

    public static ObjectNode withoutProtocolEnvelope(ObjectNode params) {
        ObjectNode copy = params.deepCopy();
        if (copy.get(META) instanceof ObjectNode meta) {
            meta.remove(PROTOCOL_ENVELOPE_KEYS);
            if (meta.isEmpty()) {
                copy.remove(META);
            }
        }
        return copy;
    }

    public static McpRequests.Request operation(String method, ObjectNode params, long id, Framer framer) {
        ObjectNode withoutMeta = params.deepCopy();
        JsonNode clientMeta = withoutMeta.remove(META);
        return withClientMeta(framer.operation(method, withoutMeta.toString(), id), clientMeta);
    }

    public static McpRequests.Request notification(String method, ObjectNode params, Framer framer) {
        ObjectNode withoutMeta = params.deepCopy();
        JsonNode clientMeta = withoutMeta.remove(META);
        return withClientMeta(framer.notification(method, withoutMeta.toString()), clientMeta);
    }

    private static Set<String> protocolEnvelopeKeys() {
        try {
            JsonNode envelope = McpObjectMapper.INSTANCE
                    .readTree(McpRequests.modernParams(ClientProfile.of("envelope", "0"), "{}")).path(META);
            Set<String> keys = new HashSet<>();
            envelope.fieldNames().forEachRemaining(keys::add);
            return Set.copyOf(keys);
        } catch (JsonProcessingException unparsable) {
            throw new UncheckedIOException(unparsable);
        }
    }

    private static McpRequests.Request withClientMeta(McpRequests.Request framed, JsonNode clientMeta) {
        if (!(clientMeta instanceof ObjectNode)) {
            return framed;
        }
        try {
            ObjectNode message = (ObjectNode) McpObjectMapper.INSTANCE.readTree(framed.body());
            ObjectNode params = message.path("params").isObject()
                    ? (ObjectNode) message.get("params") : message.putObject("params");
            ObjectNode meta = params.path(META).isObject() ? (ObjectNode) params.get(META) : params.putObject(META);
            clientMeta.fields().forEachRemaining(field -> {
                if (!meta.has(field.getKey())) {
                    meta.set(field.getKey(), field.getValue());
                }
            });
            return new McpRequests.Request(message.toString(), framed.headers());
        } catch (JsonProcessingException | ClassCastException unframed) {
            return framed;
        }
    }
}
