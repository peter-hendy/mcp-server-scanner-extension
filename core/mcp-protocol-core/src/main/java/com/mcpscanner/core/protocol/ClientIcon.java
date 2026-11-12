package com.mcpscanner.core.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ClientIcon(String src, String mimeType, List<String> sizes, String theme) {
    public ClientIcon {
        Objects.requireNonNull(src);
        if (src.isBlank()) throw new IllegalArgumentException("icon src required");
        sizes = sizes == null ? null : List.copyOf(sizes);
    }

    public static ClientIcon of(String src) { return new ClientIcon(src, null, null, null); }

    Map<String, Object> fields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("src", src);
        if (mimeType != null) fields.put("mimeType", mimeType);
        if (sizes != null) fields.put("sizes", sizes);
        if (theme != null) fields.put("theme", theme);
        return fields;
    }
}
