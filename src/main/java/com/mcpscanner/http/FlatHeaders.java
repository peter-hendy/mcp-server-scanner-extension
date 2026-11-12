package com.mcpscanner.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlatHeaders {

    private static final String CHALLENGE = "WWW-Authenticate";

    private FlatHeaders() {
    }

    public static Map<String, String> of(Map<String, List<String>> grouped) {
        Map<String, String> flat = new LinkedHashMap<>();
        grouped.forEach((name, values) -> {
            if (!values.isEmpty()) flat.put(name, flatten(name, values));
        });
        return flat;
    }

    private static String flatten(String name, List<String> values) {
        return name.equalsIgnoreCase(CHALLENGE) ? String.join(", ", values) : values.get(0);
    }
}
