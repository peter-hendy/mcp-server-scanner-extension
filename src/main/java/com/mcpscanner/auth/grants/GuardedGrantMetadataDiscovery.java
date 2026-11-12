package com.mcpscanner.auth.grants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.BurpMetadataClient;
import com.mcpscanner.auth.oauth.OAuthHttpTimeouts;
import com.mcpscanner.auth.oauth.ExpectedIssuer;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.mcp.McpObjectMapper;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.mcpscanner.core.oauth.OAuthDiscoveryPolicy;
import com.mcpscanner.core.oauth.OAuthChallengeParser;
import com.mcpscanner.core.oauth.BearerChallenge;
import com.mcpscanner.core.oauth.OAuthFlowException;
import com.mcpscanner.core.oauth.ProtectedResourceMetadata;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class GuardedGrantMetadataDiscovery {
    record Metadata(ProtectedResourceMetadata resource, URI issuer, URI tokenEndpoint, Map<String, Object> document) {
        @Override public String toString() { return "OptionalGrantMetadata[redacted]"; }
        Set<String> strings(String field) {
            if (!document.containsKey(field)) return Set.of();
            if (!(document.get(field) instanceof List<?> values)) throw invalid();
            var result = new LinkedHashSet<String>();
            for (Object value : values) {
                if (!(value instanceof String text) || text.isBlank()) throw invalid();
                result.add(text);
            }
            return Set.copyOf(result);
        }
    }

    private final BurpHttpRequestSender sender;
    private final BurpMetadataClient metadataClient;
    private final SuspiciousDestinationGate gate;
    private final OAuthDiscoveryPolicy policy = new OAuthDiscoveryPolicy();

    GuardedGrantMetadataDiscovery(BurpHttpRequestSender sender, SuspiciousDestinationGate gate) {
        this.sender = Objects.requireNonNull(sender);
        this.metadataClient = new BurpMetadataClient(sender);
        this.gate = Objects.requireNonNull(gate);
    }

    Metadata discover(URI endpoint, URI expectedIssuer) {
        try {
            var standard = discoverCandidates(endpoint, expectedIssuer, Optional.empty());
            if (standard.isPresent()) return standard.get();
            Optional<BearerChallenge> challenge = challenge(endpoint);
            if (challenge.isPresent() && challenge.get().resourceMetadata() != null) {
                return discoverCandidates(endpoint, expectedIssuer, challenge).orElseThrow(GuardedGrantMetadataDiscovery::invalid);
            }
            throw invalid();
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private Optional<Metadata> discoverCandidates(URI endpoint, URI expectedIssuer, Optional<BearerChallenge> challenge) {
        for (var candidate : policy.protectedResourceCandidates(endpoint, challenge)) {
            var document = fetch(candidate.location(), endpoint, "Protected Resource Metadata");
            if (document.isEmpty()) continue;
            var resource = policy.validateResourceMetadata(candidate, document.get());
            URI issuer = resource.issuers().stream()
                    .filter(new ExpectedIssuer(expectedIssuer)::matches)
                    .findFirst().orElseThrow(GuardedGrantMetadataDiscovery::invalid);
            for (URI location : policy.authorizationServerLocations(issuer)) {
                var server = fetch(location, endpoint, "Authorization Server Metadata");
                if (server.isEmpty()) continue;
                policy.validateIssuerMetadata(issuer, server.get());
                Object token = server.get().get("token_endpoint");
                if (!(token instanceof String text)) throw invalid();
                return Optional.of(new Metadata(resource, issuer, URI.create(text), server.get()));
            }
            throw invalid();
        }
        return Optional.empty();
    }

    private Optional<BearerChallenge> challenge(URI endpoint) {
        if (gate.evaluate(endpoint, FetchPurpose.of("MCP Authentication Challenge", endpoint)).isDenied()) throw invalid();
        try {
            var response = sender.send(OAuthHttpTimeouts.apply(new HTTPRequest(HTTPRequest.Method.GET, endpoint)));
            if (response.getStatusCode() != 401) throw invalid();
            var headers = response.getHeaderMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("WWW-Authenticate"))
                    .flatMap(entry -> entry.getValue().stream()).toList();
            return new OAuthChallengeParser().parse(headers);
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private Optional<Map<String, Object>> fetch(URI location, URI endpoint, String purpose) {
        try {
            var response = metadataClient.fetch(location,
                    destination -> gate.evaluate(destination, FetchPurpose.of(purpose, endpoint, location)).isAllowed());
            if (response.status() == 404) return Optional.empty();
            if (response.status() != 200) throw invalid();
            Map<String, Object> document = McpObjectMapper.INSTANCE.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(response.body());
            return Optional.of(document);
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private static OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION,
                "Optional grant metadata is unavailable or invalid for the configured issuer");
    }
}
