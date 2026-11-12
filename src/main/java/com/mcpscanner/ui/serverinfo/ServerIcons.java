package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

public final class ServerIcons {

    private static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";

    private ServerIcons() {
    }

    public static JsonNode of(JsonNode result, JsonNode advertisedElsewhere) {
        JsonNode icons = serverInfo(result).path("icons");
        return icons.isArray() ? icons : advertisedElsewhere;
    }

    private static JsonNode serverInfo(JsonNode result) {
        JsonNode fromMeta = result.path("_meta").path(SERVER_INFO_META);
        return fromMeta.isObject() && !fromMeta.isEmpty() ? fromMeta : result.path("serverInfo");
    }
}
