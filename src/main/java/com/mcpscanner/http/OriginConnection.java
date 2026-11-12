package com.mcpscanner.http;

import java.util.Optional;

enum OriginConnection {
    UNKNOWN,
    HTTP_1,
    HTTP_2,
    CLOSING;

    private static final String HTTP_1_PREFIX = "HTTP/1";
    private static final String HTTP_2_PREFIX = "HTTP/2";

    static Optional<OriginConnection> answeredWith(String httpVersion) {
        if (httpVersion == null) return Optional.empty();
        if (httpVersion.startsWith(HTTP_2_PREFIX)) return Optional.of(HTTP_2);
        return httpVersion.startsWith(HTTP_1_PREFIX) ? Optional.of(HTTP_1) : Optional.empty();
    }
}
