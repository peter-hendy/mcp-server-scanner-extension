package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class BareRequests {

    private static final String HOST = "Host";

    private BareRequests() {
    }

    public static HttpRequest fromUrl(String url) {
        HttpRequest template = HttpRequest.httpRequestFromUrl(url);
        List<HttpHeader> browserDefaults = template.headers().stream()
                .filter(header -> !header.name().equalsIgnoreCase(HOST))
                .toList();
        return browserDefaults.isEmpty() ? template : template.withRemovedHeaders(browserDefaults);
    }
}
