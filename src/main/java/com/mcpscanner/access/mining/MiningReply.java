package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public record MiningReply(int status, JsonNode result) {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int UNANSWERED = 0;

    public MiningReply {
        result = result == null ? MissingNode.getInstance() : result;
    }

    public static MiningReply of(int status, JsonNode result) {
        return new MiningReply(status, result);
    }

    public static MiningReply unanswered() {
        return new MiningReply(UNANSWERED, null);
    }

    public boolean rateLimited() {
        return status == TOO_MANY_REQUESTS;
    }

    public boolean usable() {
        return status / 100 == 2 && result.isObject() && !result.path("isError").asBoolean(false);
    }
}
