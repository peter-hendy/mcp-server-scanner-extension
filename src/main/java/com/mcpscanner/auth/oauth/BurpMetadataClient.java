package com.mcpscanner.auth.oauth;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPResponse;
import com.mcpscanner.core.oauth.MetadataRedirectPolicy;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class BurpMetadataClient {

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final MetadataRedirectPolicy.Response NO_RESPONSE = new MetadataRedirectPolicy.Response(0, null, null);

    private final BurpHttpRequestSender sender;
    private final MetadataRedirectPolicy redirectPolicy = new MetadataRedirectPolicy();

    public BurpMetadataClient(BurpHttpRequestSender sender) {
        this.sender = Objects.requireNonNull(sender);
    }

    public MetadataRedirectPolicy.Response fetch(URI location, Predicate<URI> allowDestination) {
        return redirectPolicy.fetch(location, allowDestination, this::get);
    }

    MetadataRedirectPolicy.Response get(URI location) {
        try {
            ReadOnlyHTTPResponse response = sender.send(OAuthHttpTimeouts.apply(new HTTPRequest(HTTPRequest.Method.GET, location)));
            int status = response.getStatusCode();
            if (REDIRECT_STATUSES.contains(status)) {
                return new MetadataRedirectPolicy.Response(status, singleLocation(response), null);
            }
            return new MetadataRedirectPolicy.Response(status, null, response.getBody());
        } catch (IOException transportFailure) {
            return NO_RESPONSE;
        }
    }

    private static String singleLocation(ReadOnlyHTTPResponse response) {
        List<String> locations = response.getHeaderMap().entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase("Location"))
                .flatMap(header -> header.getValue().stream())
                .toList();
        if (locations.size() > 1) {
            throw new IllegalArgumentException("Ambiguous metadata redirect");
        }
        return locations.isEmpty() ? null : locations.get(0);
    }
}
