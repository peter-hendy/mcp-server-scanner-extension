package com.mcpscanner.mcp;

import com.mcpscanner.core.connection.SseSessionIds;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LegacySseRoutes {

    private final Set<String> queryRoutes = ConcurrentHashMap.newKeySet();
    private final Set<String> segmentRoutes = ConcurrentHashMap.newKeySet();
    private final Map<String, String> firstSegments = new ConcurrentHashMap<>();

    public void observe(String messageUrl) {
        parsed(messageUrl).ifPresent(uri -> observe(messageUrl, uri));
    }

    public String stableUrl(String url) {
        return parsed(url).map(uri -> stable(url, uri)).orElse(url);
    }

    private void observe(String messageUrl, URI uri) {
        if (SseSessionIds.from(messageUrl).isPresent()) {
            queryRoutes.add(origin(uri) + uri.getRawPath());
            return;
        }
        String parent = parentOf(uri);
        String segment = lastSegmentOf(uri);
        if (!segment.isEmpty() && !segment.equals(firstSegments.computeIfAbsent(parent, ignored -> segment))) {
            segmentRoutes.add(parent);
        }
    }

    private String stable(String url, URI uri) {
        if (queryRoutes.contains(origin(uri) + uri.getRawPath())) {
            return SseSessionIds.from(url).map(id -> withoutSessionId(url, uri, id)).orElse(url);
        }
        String parent = parentOf(uri);
        return segmentRoutes.contains(parent) && !lastSegmentOf(uri).isEmpty() ? parent + query(uri.getRawQuery()) : url;
    }

    private static String withoutSessionId(String url, URI uri, String sessionId) {
        String kept = Arrays.stream(uri.getRawQuery().split("&"))
                .filter(parameter -> !sessionId.equals(valueOf(parameter)))
                .collect(Collectors.joining("&"));
        return url.substring(0, url.indexOf('?')) + query(kept);
    }

    private static String valueOf(String parameter) {
        int equals = parameter.indexOf('=');
        return equals < 0 ? "" : URLDecoder.decode(parameter.substring(equals + 1), StandardCharsets.UTF_8);
    }

    private static String query(String rawQuery) {
        return rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery;
    }

    private static String parentOf(URI uri) {
        String path = uri.getRawPath();
        return origin(uri) + path.substring(0, Math.max(path.lastIndexOf('/'), 0));
    }

    private static String lastSegmentOf(URI uri) {
        String path = uri.getRawPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String origin(URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
    }

    private static Optional<URI> parsed(String url) {
        if (url == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url);
            return uri.getScheme() == null || uri.getRawAuthority() == null || uri.getRawPath() == null
                    ? Optional.empty() : Optional.of(uri);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
