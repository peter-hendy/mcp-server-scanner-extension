package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class PrmToAsResolver {

    private static final String AUTHORIZATION_SERVERS_FIELD = "authorization_servers";
    private static final String PURPOSE_PRM_DOC = "Protected Resource Metadata";
    private static final String PURPOSE_AS_DOC = "Authorization Server Metadata";

    private final MetadataDocumentFetcher fetcher;
    private final McpEventLog eventLog;

    public PrmToAsResolver(Http http, McpEventLog eventLog, SuspiciousDestinationGate gate) {
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
        this.fetcher = new MetadataDocumentFetcher(http, gate, this.eventLog);
    }

    public Optional<DiscoveredMetadata> resolve(URI prmDocUrl, DiscoverySource source) {
        return resolve(prmDocUrl, source, null);
    }

    public Optional<DiscoveredMetadata> resolve(URI prmDocUrl, DiscoverySource source, URI mcpEndpoint) {
        return fetcher.fetch(prmDocUrl, FetchPurpose.of(PURPOSE_PRM_DOC, mcpEndpoint))
                .flatMap(body -> resolveFetchedPrm(prmDocUrl, source, mcpEndpoint, body));
    }

    Optional<DiscoveredMetadata> resolveFetchedPrm(
            URI prmDocUrl, DiscoverySource source, URI mcpEndpoint, String prmBodyText) {
        Optional<JsonNode> prmJson = parseProtectedResourceMetadata(prmBodyText);
        if (prmJson.isEmpty()) {
            return Optional.empty();
        }
        if (!MetadataParsers.matchesProtectedResource(prmJson.get(), mcpEndpoint)) {
            throw new OAuthException("Protected resource metadata resource does not match the MCP endpoint");
        }
        Optional<URI> asIssuerOpt = extractAuthorizationServer(prmJson.get());
        if (asIssuerOpt.isEmpty()) {
            return Optional.empty();
        }

        URI asIssuer = asIssuerOpt.get();
        checkPrmAuthorizationServerHost(prmDocUrl, asIssuer, mcpEndpoint);

        return fetchAsMetadata(asIssuer, prmDocUrl, mcpEndpoint)
                .map(asMetadata -> new DiscoveredMetadata(asIssuer, source, asMetadata,
                        MetadataParsers.protectedResourceScopes(prmJson.get()),
                        new ProtectedResourceDocument(prmDocUrl, prmBodyText), null));
    }

    private Optional<JsonNode> parseProtectedResourceMetadata(String body) {
        if (body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode metadata = McpObjectMapper.INSTANCE.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (!metadata.isObject()) {
                throw new OAuthException("Invalid protected resource metadata document");
            }
            return Optional.of(metadata);
        } catch (IOException malformedDocument) {
            throw new OAuthException("Invalid protected resource metadata document");
        }
    }

    private void checkPrmAuthorizationServerHost(URI prmDocUrl,
                                                  URI asIssuer,
                                                  URI mcpEndpoint) {
        String mcpHost = mcpEndpoint != null ? mcpEndpoint.getHost() : prmDocUrl.getHost();
        if (mcpHost == null) {
            return;
        }
        String asHost = asIssuer.getHost();
        if (asHost == null || asHost.equalsIgnoreCase(mcpHost)) {
            return;
        }
        warn("PRM authorization_servers host mismatch: MCP endpoint host=" + mcpHost
                + ", AS issuer host=" + asHost + ". Proceeding permissively.");
    }

    private Optional<URI> extractAuthorizationServer(JsonNode prmDoc) {
        JsonNode servers = prmDoc.get(AUTHORIZATION_SERVERS_FIELD);
        if (servers == null || !servers.isArray() || servers.isEmpty()) {
            warn("PRM document missing 'authorization_servers' array");
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(servers.get(0).asText()));
        } catch (IllegalArgumentException e) {
            warn("PRM 'authorization_servers' contains invalid URI: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AuthorizationServerMetadata> fetchAsMetadata(URI issuer, URI sourceUrl, URI mcpEndpoint) {
        for (URI asUrl : OAuthWellKnownPaths.authorizationServerMetadataUrls(issuer)) {
            Optional<AuthorizationServerMetadata> metadata = fetcher.fetch(
                    asUrl, FetchPurpose.of(PURPOSE_AS_DOC, mcpEndpoint, sourceUrl)).flatMap(body ->
                    MetadataParsers.parseValidatedAs(body, issuer, eventLog, asUrl.toString()));
            if (metadata.isPresent()) {
                return metadata;
            }
        }
        return Optional.empty();
    }

    private void warn(String message) {
        eventLog.warn(message);
    }

}
