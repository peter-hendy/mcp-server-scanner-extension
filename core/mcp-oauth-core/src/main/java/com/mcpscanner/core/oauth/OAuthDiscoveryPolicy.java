package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

public final class OAuthDiscoveryPolicy {
    public record ProtectedResourceCandidate(URI location, List<URI> expectedResources) {
        public ProtectedResourceCandidate {
            expectedResources = List.copyOf(expectedResources);
        }
    }
    public List<URI> protectedResourceLocations(URI resource, Optional<BearerChallenge> challenge) {
        return protectedResourceCandidates(resource, challenge).stream().map(ProtectedResourceCandidate::location).toList();
    }
    public List<ProtectedResourceCandidate> protectedResourceCandidates(URI resource, Optional<BearerChallenge> challenge) {
        validateLocation(resource);
        if (challenge.isPresent() && challenge.get().resourceMetadata() != null) {
            if (!advertisableFrom(resource, challenge.get().resourceMetadata())) throw invalidMetadata();
            return List.of(new ProtectedResourceCandidate(challenge.get().resourceMetadata(), List.of(resource)));
        }
        URI origin = URI.create(resource.getScheme() + "://" + resource.getRawAuthority());
        String path = resource.getRawPath();
        URI root = URI.create(origin + "/.well-known/oauth-protected-resource");
        var rootCandidate = new ProtectedResourceCandidate(root, List.of(resource));
        return path == null || path.isEmpty() || path.equals("/") ? List.of(rootCandidate)
                : List.of(new ProtectedResourceCandidate(URI.create(root + path), List.of(resource)), rootCandidate);
    }
    public ProtectedResourceMetadata validateResourceMetadata(ProtectedResourceCandidate candidate, Map<String, Object> document) {
        for (URI expected : candidate.expectedResources()) {
            if (sameResource(expected, document.get("resource"))) return validateResourceMetadata(expected, document);
        }
        throw new OAuthPolicyException(OAuthPolicyException.Reason.RESOURCE_MISMATCH);
    }
    public Set<String> initialScopes(Optional<BearerChallenge> challenge, Set<String> metadataScopes) {
        return challenge.isPresent() && challenge.get().scopeSpecified() ? challenge.get().scopes() : Set.copyOf(metadataScopes);
    }
    public List<URI> authorizationServerLocations(URI issuer) {
        validateLocation(issuer);
        if (issuer.getRawQuery() != null) throw invalidMetadata();
        String origin = issuer.getScheme() + "://" + issuer.getRawAuthority();
        String path = issuer.getRawPath();
        if (path == null || path.isEmpty() || path.equals("/")) return List.of(
                URI.create(origin + "/.well-known/oauth-authorization-server"),
                URI.create(origin + "/.well-known/openid-configuration"));
        return List.of(URI.create(origin + "/.well-known/oauth-authorization-server" + path),
                URI.create(origin + "/.well-known/openid-configuration" + path),
                URI.create(issuer + (path.endsWith("/") ? "" : "/") + ".well-known/openid-configuration"));
    }
    public ProtectedResourceMetadata validateResourceMetadata(URI expectedResource, Map<String, Object> document) {
        validateLocation(expectedResource);
        if (!sameResource(expectedResource, document.get("resource")))
            throw new OAuthPolicyException(OAuthPolicyException.Reason.RESOURCE_MISMATCH);
        if (!(document.get("authorization_servers") instanceof List<?> values) || values.isEmpty()) throw invalidMetadata();
        var issuers = new ArrayList<URI>();
        for (Object value : values) {
            URI issuer = parseLocation(value);
            if (issuer.getRawQuery() != null) throw invalidMetadata();
            if (!issuers.contains(issuer)) issuers.add(issuer);
        }
        var scopes = new java.util.LinkedHashSet<String>();
        if (document.containsKey("scopes_supported")) {
            if (!(document.get("scopes_supported") instanceof List<?> valuesScopes)) throw invalidMetadata();
            for (Object scope : valuesScopes) {
                if (!(scope instanceof String text) || !ScopeValues.valid(text)) throw invalidMetadata();
                scopes.add(text);
            }
        }
        return new ProtectedResourceMetadata(parseLocation(document.get("resource")), issuers, scopes);
    }
    public void validateIssuerMetadata(URI expectedIssuer, Map<String, Object> document) {
        validateLocation(expectedIssuer);
        if (!expectedIssuer.toString().equals(document.get("issuer")))
            throw new OAuthPolicyException(OAuthPolicyException.Reason.ISSUER_MISMATCH);
    }
    private boolean sameResource(URI expected, Object advertised) {
        if (!(advertised instanceof String text)) return false;
        try {
            URI resource = URI.create(text);
            if (!validLocation(resource) || !sameOrigin(expected, resource)
                    || !unambiguousResourcePath(expected) || !unambiguousResourcePath(resource)) return false;
            String endpointPath = expected.getRawPath();
            String resourcePath = resource.getRawPath();
            if (resource.getRawQuery() != null) {
                return endpointPath.equals(resourcePath)
                        && resource.getRawQuery().equals(expected.getRawQuery());
            }
            if (resourcePath.isEmpty() || resourcePath.equals("/")) return true;
            return endpointPath.equals(resourcePath)
                    || endpointPath.startsWith(resourcePath.endsWith("/") ? resourcePath : resourcePath + "/");
        } catch (IllegalArgumentException failure) { return false; }
    }
    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost()) && effectivePort(first) == effectivePort(second);
    }
    private int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
    private boolean unambiguousResourcePath(URI uri) {
        if (uri.getRawFragment() != null) return false;
        String path = uri.getRawPath();
        if (path == null || path.contains("//") || path.toLowerCase(java.util.Locale.ROOT).matches(".*%(2f|5c|25).*")) return false;
        for (String segment : uri.getPath().split("/")) {
            if (segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }
    private URI parseLocation(Object value) {
        if (!(value instanceof String text)) throw invalidMetadata();
        try {
            URI uri = URI.create(text);
            validateLocation(uri);
            return uri;
        } catch (IllegalArgumentException failure) { throw invalidMetadata(); }
    }
    private OAuthPolicyException invalidMetadata() { return new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_METADATA); }
    static boolean validLocation(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) return false;
        return "https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()) && LoopbackHost.matches(uri.getHost());
    }
    static boolean advertisableFrom(URI resource, URI advertised) {
        return validLocation(advertised)
                && (!LoopbackHost.matches(advertised.getHost()) || LoopbackHost.matches(resource.getHost()));
    }
    private void validateLocation(URI uri) {
        if (!validLocation(uri)) throw new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_METADATA);
    }
}
