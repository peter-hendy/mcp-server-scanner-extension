package com.mcpscanner.engine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

record IssueLocation(String scheme, String host, int port, String path) {

    private static final int UNKNOWN_PORT = -1;

    static IssueLocation of(String url) {
        String spelled = Objects.requireNonNullElse(url, "");
        try {
            return parsed(new URI(spelled));
        } catch (URISyntaxException unparseable) {
            return new IssueLocation("", "", UNKNOWN_PORT, withoutQuery(spelled));
        }
    }

    String siteMapPrefix() {
        return scheme.isEmpty() ? path : scheme + "://" + host;
    }

    private static IssueLocation parsed(URI uri) {
        String scheme = lowerCase(uri.getScheme());
        String path = Objects.requireNonNullElse(uri.getRawPath(), "");
        return new IssueLocation(scheme, lowerCase(uri.getHost()), port(scheme, uri.getPort()),
                path.isEmpty() ? "/" : path);
    }

    private static int port(String scheme, int spelled) {
        if (spelled != UNKNOWN_PORT) {
            return spelled;
        }
        return switch (scheme) {
            case "https" -> 443;
            case "http" -> 80;
            default -> UNKNOWN_PORT;
        };
    }

    private static String lowerCase(String part) {
        return part == null ? "" : part.toLowerCase(Locale.ROOT);
    }

    private static String withoutQuery(String url) {
        int end = url.length();
        for (char marker : new char[]{'?', '#'}) {
            int index = url.indexOf(marker);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return url.substring(0, end);
    }
}
