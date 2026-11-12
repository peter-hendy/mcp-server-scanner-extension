package com.mcpscanner.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

final class JsonRpcIdRewriter {

    private static final String ID_FIELD = "id";
    private static final ObjectMapper MAPPER = McpObjectMapper.INSTANCE;

    private final LongSupplier idAllocator;
    private final McpEventLog eventLog;

    JsonRpcIdRewriter(LongSupplier idAllocator, McpEventLog eventLog) {
        this.idAllocator = idAllocator;
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
    }

    String rewrite(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root instanceof ObjectNode object) {
                return rewriteObjectId(object) ? MAPPER.writeValueAsString(object) : body;
            }
            if (root instanceof ArrayNode array) {
                return rewriteArrayIds(array) ? MAPPER.writeValueAsString(array) : body;
            }
            return body;
        } catch (JsonProcessingException malformed) {
            eventLog.debug("Forwarded malformed JSON-RPC body unchanged: " + malformed.getOriginalMessage());
            return body;
        }
    }

    String restoreId(String replyBody, String rewrittenBody, String scannerBody) {
        Optional<TopLevelId> replyId = TopLevelId.in(replyBody);
        Optional<TopLevelId> rewrittenId = TopLevelId.in(rewrittenBody);
        Optional<TopLevelId> scannerId = TopLevelId.in(scannerBody);
        if (replyId.isEmpty() || rewrittenId.isEmpty() || scannerId.isEmpty()
                || !replyId.get().text().equals(rewrittenId.get().text())) {
            return replyBody;
        }
        return replyId.get().spliceInto(replyBody, scannerId.get());
    }

    // Force long ids so within-session uniqueness holds regardless of what the
    // client builder chose - stringified longs collide less often in FastMCP's
    // _request_streams[str(id)] routing than mixed string/number ids would.
    private boolean rewriteObjectId(ObjectNode object) {
        if (!object.has(ID_FIELD) || !(object.get(ID_FIELD).isNull()
                || object.get(ID_FIELD).isTextual() || object.get(ID_FIELD).isNumber())) {
            return false;
        }
        object.set(ID_FIELD, LongNode.valueOf(idAllocator.getAsLong()));
        return true;
    }

    private boolean rewriteArrayIds(ArrayNode array) {
        boolean rewroteAny = false;
        for (JsonNode element : array) {
            if (element instanceof ObjectNode object && rewriteObjectId(object)) {
                rewroteAny = true;
            }
        }
        return rewroteAny;
    }
}
