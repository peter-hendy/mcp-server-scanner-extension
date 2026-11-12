package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.oauth.ExpectedIssuer;
import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class AsWellKnownProbe implements MetadataProbe {

    private static final String PURPOSE = "Authorization Server Metadata (.well-known)";
    private final MetadataDocumentFetcher fetcher;
    private final McpEventLog eventLog;

    public AsWellKnownProbe(Http http, SuspiciousDestinationGate gate, McpEventLog eventLog) {
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
        this.fetcher = new MetadataDocumentFetcher(http, gate, this.eventLog);
    }

    @Override
    public Optional<DiscoveredMetadata> probe(URI mcpResource) {
        Optional<URI> issuer = rootIssuer(mcpResource);
        if (issuer.isEmpty()) {
            return Optional.empty();
        }
        for (URI url : OAuthWellKnownPaths.authorizationServerMetadataUrls(issuer.get())) {
            Optional<AuthorizationServerMetadata> metadata = fetcher.fetchOk(
                    url, FetchPurpose.of(PURPOSE, mcpResource)).flatMap(body ->
                    parseRootMetadata(body, issuer.get(), url));
            if (metadata.isPresent()) {
                return metadata.map(AsWellKnownProbe::toDiscoveredMetadata);
            }
        }
        return Optional.empty();
    }

    private Optional<AuthorizationServerMetadata> parseRootMetadata(String body, URI rootIssuer, URI url) {
        return MetadataParsers.parseAs(body, eventLog, url.toString()).map(metadata -> {
            if (!new ExpectedIssuer(rootIssuer).matchesAllowingTrailingSlash(metadata.getIssuer().getValue())) {
                throw new OAuthException("OAuth metadata issuer mismatch");
            }
            return metadata;
        });
    }

    private static Optional<URI> rootIssuer(URI mcpResource) {
        try {
            return Optional.of(new URI(
                    mcpResource.getScheme(),
                    null,
                    mcpResource.getHost(),
                    mcpResource.getPort(),
                    null,
                    null,
                    null));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static DiscoveredMetadata toDiscoveredMetadata(AuthorizationServerMetadata metadata) {
        return new DiscoveredMetadata(URI.create(metadata.getIssuer().getValue()), DiscoverySource.AS_WELL_KNOWN, metadata);
    }

}
