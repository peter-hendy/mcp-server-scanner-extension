package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;

public final class Markers {

    private Markers() {
    }

    public static boolean mentionedBy(HttpRequest request, String marker) {
        if (marker.isEmpty() || request == null) {
            return false;
        }
        StringBuilder text = new StringBuilder(request.url());
        request.headers().forEach(header -> text.append('\n').append(header.value()));
        text.append('\n').append(request.bodyToString());
        return text.toString().contains(marker);
    }

    public static String of(Optional<String> marker) {
        return marker.filter(value -> !value.isBlank()).orElse("");
    }
}
