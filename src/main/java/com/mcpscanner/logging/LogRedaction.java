package com.mcpscanner.logging;

public final class LogRedaction {

    private static final String REDACTED_QUERY = "?<redacted>";

    private LogRedaction() {}

    public static String redactQuery(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + REDACTED_QUERY;
    }
}
