package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class MetadataRedirectPolicy {
    private static final int MAX_REDIRECTS = 2;

    public record Response(int status, String location, String body) {}

    public Response fetch(URI initial, Predicate<URI> validateDestination, Function<URI, Response> get) {
        URI current = initial;
        Set<URI> visited = new HashSet<>();
        for (int redirects = 0; ; redirects++) {
            validateUri(current);
            if (!visited.add(current)) throw new IllegalArgumentException("Metadata redirect loop");
            if (!validateDestination.test(current)) throw new IllegalArgumentException("Metadata destination rejected");
            Response response = get.apply(current);
            if (!redirectStatus(response.status())) return response;
            if (redirects == MAX_REDIRECTS) throw new IllegalArgumentException("Too many metadata redirects");
            URI destination = resolve(current, response.location());
            if (isDowngrade(current, destination)) throw new IllegalArgumentException("Metadata redirect downgrade");
            current = destination;
        }
    }

    private URI resolve(URI current, String location) {
        if (location == null || location.isEmpty()) throw new IllegalArgumentException("Missing metadata redirect location");
        validateCharacters(location);
        try {
            URI destination = current.resolve(URI.create(location));
            validateUri(destination);
            return destination;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid metadata redirect location", failure);
        }
    }

    private void validateUri(URI uri) {
        if (!OAuthDiscoveryPolicy.validLocation(uri)) throw new IllegalArgumentException("Unsafe metadata URI");
        validateCharacters(uri.toString());
    }

    private void validateCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || Character.isWhitespace(character) || Character.isISOControl(character)) {
                throw new IllegalArgumentException("Unsafe metadata URI character");
            }
        }
    }

    private boolean isDowngrade(URI current, URI destination) {
        return "https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(destination.getScheme());
    }

    private boolean redirectStatus(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }
}
