package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class CookieJar {

    private final Map<String, String> cookies = new LinkedHashMap<>();

    void collect(HttpResponse response) {
        ResponseHeaders.values(response, "Set-Cookie").stream()
                .map(header -> header.split(";", 2)[0].trim())
                .filter(pair -> pair.indexOf('=') > 0)
                .forEach(pair -> cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1)));
    }

    Map<String, String> header() {
        if (cookies.isEmpty()) {
            return Map.of();
        }
        return Map.of("Cookie", cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; ")));
    }
}
