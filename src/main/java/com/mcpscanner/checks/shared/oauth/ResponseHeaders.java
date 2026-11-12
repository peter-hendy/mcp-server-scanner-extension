package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ResponseHeaders {

    private ResponseHeaders() {
    }

    public static List<String> values(HttpResponse response, String name) {
        return response.headers().stream()
                .filter(header -> header.name().equalsIgnoreCase(name))
                .map(HttpHeader::value)
                .toList();
    }

    public static Optional<String> first(HttpResponse response, String name) {
        return values(response, name).stream().findFirst();
    }

    public static boolean isHtml(HttpResponse response) {
        return first(response, "Content-Type")
                .map(type -> type.toLowerCase(Locale.ROOT).trim().startsWith("text/html"))
                .orElse(false);
    }
}
