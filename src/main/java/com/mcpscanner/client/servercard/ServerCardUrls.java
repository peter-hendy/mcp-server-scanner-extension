package com.mcpscanner.client.servercard;

import java.net.URI;
import java.net.URISyntaxException;

public record ServerCardUrls(URI serverCard, URI aiCatalog) {

    private static final String SERVER_CARD_SEGMENT = "/server-card";
    private static final String AI_CATALOG_PATH = "/.well-known/ai-catalog.json";

    public static ServerCardUrls forEndpoint(URI endpoint) {
        return new ServerCardUrls(
                onOrigin(endpoint, pathWithoutTrailingSlash(endpoint) + SERVER_CARD_SEGMENT),
                onOrigin(endpoint, AI_CATALOG_PATH));
    }

    private static String pathWithoutTrailingSlash(URI endpoint) {
        String path = endpoint.getRawPath() == null ? "" : endpoint.getRawPath();
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static URI onOrigin(URI endpoint, String rawPath) {
        try {
            URI origin = new URI(endpoint.getScheme(), null, endpoint.getHost(), endpoint.getPort(), null, null, null);
            return URI.create(origin + rawPath);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid MCP endpoint: " + endpoint, invalid);
        }
    }
}
