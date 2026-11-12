package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class WellKnownLocations {

    private static final String PROTECTED_RESOURCE = "/.well-known/oauth-protected-resource";
    private static final String AUTHORIZATION_SERVER = "/.well-known/oauth-authorization-server";
    private static final String OPENID_CONFIGURATION = "/.well-known/openid-configuration";

    private WellKnownLocations() {
    }

    public static List<URI> protectedResourceMetadata(URI resourceEndpoint) {
        return origin(resourceEndpoint).map(origin -> distinct(
                origin + PROTECTED_RESOURCE + HttpUris.pathWithoutTrailingSlash(resourceEndpoint),
                origin + PROTECTED_RESOURCE)).orElseGet(List::of);
    }

    public static List<URI> authorizationServerMetadata(URI issuer) {
        String path = HttpUris.pathWithoutTrailingSlash(issuer);
        return origin(issuer).map(origin -> distinct(origin + AUTHORIZATION_SERVER + path,
                origin + path + AUTHORIZATION_SERVER,
                origin + OPENID_CONFIGURATION + path,
                origin + path + OPENID_CONFIGURATION,
                origin + AUTHORIZATION_SERVER)).orElseGet(List::of);
    }

    private static Optional<String> origin(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getRawAuthority() == null) {
            return Optional.empty();
        }
        return Optional.of(uri.getHost() != null ? HttpUris.origin(uri) : scheme + "://" + uri.getRawAuthority());
    }

    private static List<URI> distinct(String... locations) {
        return Stream.of(locations).map(WellKnownLocations::toUri).flatMap(Optional::stream).distinct().toList();
    }

    private static Optional<URI> toUri(String location) {
        try {
            return Optional.of(new URI(location));
        } catch (URISyntaxException malformed) {
            return Optional.empty();
        }
    }
}
