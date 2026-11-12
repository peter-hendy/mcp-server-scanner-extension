package com.mcpscanner.proxy;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectedEndpoint;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.HttpOrigin;
import com.mcpscanner.mcp.ScannerSentinels;

import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProxyRouting {

    private static final Set<TransportType> PROXIED_TRANSPORTS =
            EnumSet.of(TransportType.MODERN_HTTP, TransportType.STREAMABLE_HTTP, TransportType.SSE);

    private final ConnectedEndpoint endpoint;

    public ProxyRouting(ConnectedEndpoint endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    public boolean answersLocally(HttpRequest request) {
        return !request.hasHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER) && target(request).isPresent();
    }

    Optional<URI> target(HttpRequest request) {
        TransportType transport = endpoint.transportType();
        String resolved = endpoint.resolvedEndpoint();
        if (!PROXIED_TRANSPORTS.contains(transport) || resolved == null) {
            return Optional.empty();
        }
        if (transport != TransportType.SSE && !carriesScannerSentinel(request)) {
            return Optional.empty();
        }
        URI target = URI.create(resolved);
        return addresses(target, request) ? Optional.of(target) : Optional.empty();
    }

    private static boolean addresses(URI target, HttpRequest request) {
        return sameHostAndPort(HttpOrigin.of(target), request.httpService())
                && normalizePath(target.getPath()).equals(normalizePath(request.pathWithoutQuery()));
    }

    private static boolean sameHostAndPort(HttpOrigin target, HttpService service) {
        return target.host().equalsIgnoreCase(service.host()) && target.port() == service.port();
    }

    private static boolean carriesScannerSentinel(HttpRequest request) {
        return request.headers().stream().anyMatch(header -> ScannerSentinels.ROUTES_TO_PROXY.contains(header.name()));
    }

    private static String normalizePath(String path) {
        return (path == null || path.isEmpty()) ? "/" : path;
    }
}
