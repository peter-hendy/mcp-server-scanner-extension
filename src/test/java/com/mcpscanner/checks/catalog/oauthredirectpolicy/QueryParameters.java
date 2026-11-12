package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class QueryParameters {

    private QueryParameters() {
    }

    static Map<String, String> of(String url) {
        return of(URI.create(url));
    }

    static Map<String, String> of(URI uri) {
        return ofEncoded(uri.getRawQuery());
    }

    static Map<String, String> ofEncoded(String encoded) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return parameters;
        }
        Arrays.stream(encoded.split("&")).forEach(pair -> {
            String[] parts = pair.split("=", 2);
            parameters.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        });
        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
