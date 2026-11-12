package com.mcpscanner.checks.catalog.jwtvalidation;

import com.mcpscanner.checks.shared.oauth.AuthorizationServerMetadataFetcher;
import com.mcpscanner.checks.shared.oauth.HttpUris;
import com.mcpscanner.checks.shared.oauth.JsonDocument;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.WellKnownLocations;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class PublishedKeyDiscovery {

    private static final int MAX_ISSUERS = 3;
    private static final int MAX_KEY_MATERIALS = 8;

    private final JwkPemEncoder encoder;

    PublishedKeyDiscovery(JwkPemEncoder encoder) {
        this.encoder = encoder;
    }

    List<PublicKeyMaterial> discover(String resourceUrl, CompactJwt captured, OAuthHttp http) {
        AuthorizationServerMetadataFetcher metadata = new AuthorizationServerMetadataFetcher(http);
        List<URI> jwksUris = issuers(resourceUrl, captured, http).stream()
                .map(issuer -> metadata.fetch(issuer, document -> document.webUri("jwks_uri")))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        Map<String, PublicKeyMaterial> materials = new LinkedHashMap<>();
        for (URI jwksUri : jwksUris) {
            http.getJsonDocument(jwksUri).ifPresent(jwks -> jwks.objects("keys").forEach(jwk ->
                    encoder.encode(jwk, jwksUri.toString())
                            .forEach(material -> materials.putIfAbsent(Arrays.toString(material.bytes()), material))));
        }
        return materials.values().stream().limit(MAX_KEY_MATERIALS).toList();
    }

    private static List<URI> issuers(String resourceUrl, CompactJwt captured, OAuthHttp http) {
        Stream<URI> advertised = protectedResourceMetadata(resourceUrl, http).stream()
                .flatMap(metadata -> metadata.texts("authorization_servers").stream())
                .map(HttpUris::parse)
                .flatMap(Optional::stream);
        Stream<URI> tokenIssuer = captured.issuer().flatMap(HttpUris::parse).stream();
        return Stream.concat(advertised, tokenIssuer).distinct().limit(MAX_ISSUERS).toList();
    }

    private static Optional<JsonDocument> protectedResourceMetadata(String resourceUrl, OAuthHttp http) {
        return HttpUris.parse(resourceUrl).stream()
                .flatMap(resource -> WellKnownLocations.protectedResourceMetadata(resource).stream())
                .map(http::getJsonDocument)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
