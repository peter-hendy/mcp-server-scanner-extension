package com.mcpscanner.mcp;

import java.util.Set;
import java.util.TreeSet;

public final class McpHeaders {

    public static final String SESSION_ID = "Mcp-Session-Id";
    public static final String PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String METHOD = "Mcp-Method";
    public static final String NAME = "Mcp-Name";
    public static final String PARAM_PREFIX = "Mcp-Param-";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String ACCEPT_JSON_AND_SSE = "application/json, text/event-stream";

    private static final Set<String> PROTOCOL_HEADERS = caseInsensitive(SESSION_ID, PROTOCOL_VERSION, METHOD, NAME);

    private McpHeaders() {}

    public static boolean isProtocolHeader(String name) {
        return PROTOCOL_HEADERS.contains(name);
    }

    public static boolean isOperationHeader(String name) {
        return METHOD.equalsIgnoreCase(name) || NAME.equalsIgnoreCase(name) || PROTOCOL_VERSION.equalsIgnoreCase(name)
                || isParamHeader(name);
    }

    public static boolean isParamHeader(String name) {
        return name.regionMatches(true, 0, PARAM_PREFIX, 0, PARAM_PREFIX.length());
    }

    public static Set<String> caseInsensitive(String... names) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(Set.of(names));
        return java.util.Collections.unmodifiableSet(set);
    }
}
