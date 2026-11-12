package com.mcpscanner.mcp;

import burp.api.montoya.http.HttpService;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record HttpOrigin(String scheme, String host, int port) {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final int UNKNOWN_PORT = -1;

    public HttpOrigin {
        scheme = Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
        host = Objects.requireNonNull(host, "host").toLowerCase(Locale.ROOT);
    }

    public static HttpOrigin of(URI uri) {
        return parse(uri).orElseThrow(() -> new IllegalArgumentException("URI has no origin: " + uri));
    }

    public static HttpOrigin of(HttpService service) {
        return new HttpOrigin(service.secure() ? HTTPS : HTTP, service.host(), service.port());
    }

    public static Optional<HttpOrigin> parse(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return Optional.empty();
        }
        return Optional.of(new HttpOrigin(uri.getScheme(), uri.getHost(), effectivePort(uri)));
    }

    public static Optional<HttpOrigin> parse(String uri) {
        try {
            return parse(new URI(uri));
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }

    public static boolean sameOrigin(URI first, URI second) {
        Optional<HttpOrigin> origin = parse(first);
        return origin.isPresent() && origin.equals(parse(second));
    }

    public static int defaultPort(String scheme) {
        if (HTTPS.equalsIgnoreCase(scheme)) {
            return 443;
        }
        return HTTP.equalsIgnoreCase(scheme) ? 80 : UNKNOWN_PORT;
    }

    public boolean secure() {
        return HTTPS.equals(scheme);
    }

    public HttpService toHttpService() {
        return HttpService.httpService(host, port, secure());
    }

    public String hostHeader() {
        return port == defaultPort(scheme) ? bracketedHost() : bracketedHost() + ":" + port;
    }

    private String bracketedHost() {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    public String baseUrl() {
        return scheme + "://" + hostHeader();
    }

    @Override
    public String toString() {
        return baseUrl();
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() != UNKNOWN_PORT ? uri.getPort() : defaultPort(uri.getScheme());
    }
}
