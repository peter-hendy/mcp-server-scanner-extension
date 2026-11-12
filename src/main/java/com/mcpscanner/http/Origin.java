package com.mcpscanner.http;

import burp.api.montoya.http.HttpService;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

record Origin(String scheme, String host, int port) {

    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;

    static Origin of(HttpService service) {
        return new Origin(service.secure() ? "https" : "http", lowercase(service.host()), service.port());
    }

    static Optional<Origin> of(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return Optional.empty();
            }
            String scheme = lowercase(uri.getScheme());
            return Optional.of(new Origin(scheme, lowercase(uri.getHost()), portOf(uri, scheme)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static int portOf(URI uri, String scheme) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equals(scheme) ? HTTPS_PORT : HTTP_PORT;
    }

    private static String lowercase(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
