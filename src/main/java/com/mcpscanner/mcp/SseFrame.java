package com.mcpscanner.mcp;

public record SseFrame(String eventType, String data) {

    private static final String MESSAGE_EVENT = "message";
    private static final String COMMENT_PREFIX = ":";
    private static final String EVENT_FIELD = "event";
    private static final String DATA_FIELD = "data";

    public static SseFrame parse(String frame) {
        String eventType = null;
        StringBuilder data = null;
        for (String line : frame.split("\n", -1)) {
            if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            String field = fieldName(line);
            String value = fieldValue(line);
            if (EVENT_FIELD.equals(field)) {
                eventType = value.trim();
            } else if (DATA_FIELD.equals(field)) {
                data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
            }
        }
        return new SseFrame(eventType, data == null ? null : data.toString());
    }

    public boolean hasData() {
        return data != null;
    }

    public boolean isMessage() {
        return eventType == null || eventType.isEmpty() || MESSAGE_EVENT.equals(eventType);
    }

    public boolean isEvent(String type) {
        return type.equals(eventType);
    }

    private static String fieldName(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? line : line.substring(0, colon);
    }

    private static String fieldValue(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return "";
        }
        String value = line.substring(colon + 1);
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
