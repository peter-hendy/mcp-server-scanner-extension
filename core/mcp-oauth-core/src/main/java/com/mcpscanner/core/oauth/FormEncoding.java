package com.mcpscanner.core.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

final class FormEncoding {
    private FormEncoding() {}
    static String encode(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
