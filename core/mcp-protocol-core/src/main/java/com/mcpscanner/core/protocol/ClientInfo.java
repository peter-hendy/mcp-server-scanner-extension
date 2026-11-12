package com.mcpscanner.core.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ClientInfo(String name, String version, String title, String description,
                         String websiteUrl, List<ClientIcon> icons) {
    private static final int MAX_JSON_BYTES = 64 * 1024;

    public ClientInfo {
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        icons = icons == null || icons.isEmpty() ? null : List.copyOf(icons);
    }

    public static ClientInfo of(String name, String version) {
        return new ClientInfo(name, version, null, null, null, null);
    }

    public String json() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("version", version);
        if (title != null) fields.put("title", title);
        if (description != null) fields.put("description", description);
        if (websiteUrl != null) fields.put("websiteUrl", websiteUrl);
        if (icons != null) fields.put("icons", icons.stream().map(ClientIcon::fields).toList());
        return JsonWriter.write(fields, MAX_JSON_BYTES);
    }
}
