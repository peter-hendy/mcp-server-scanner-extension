package com.mcpscanner.core.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OAuthScanPolicy {
    private static final Pattern RESOURCE_METADATA = Pattern.compile(
        "(?i)(?:^|[,\\s])resource_metadata\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern CHALLENGE_SCOPE = Pattern.compile(
        "(?i)(?:^|[,\\s])scope\\s*=\\s*\"([^\"]*)\"");

    public record ResourceCandidate(URI location, URI expectedResource) {}

    public List<ResourceCandidate> resourceMetadataCandidates(URI endpoint, Optional<String> challenge) {
        return resourceMetadataCandidates(endpoint, challenge.stream().toList());
    }

    public List<ResourceCandidate> resourceMetadataCandidates(URI endpoint, List<String> challenges) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(challenges);
        if (safeHttpUri(endpoint.toString()) == null) return List.of();
        for (String challenge : challenges) {
            Matcher matcher = RESOURCE_METADATA.matcher(challenge);
            if (matcher.find()) {
                URI advertised = advertisedLocation(endpoint, matcher.group(1));
                return advertised == null ? List.of() : List.of(new ResourceCandidate(advertised, endpoint));
            }
        }
        String path = endpoint.getRawPath();
        URI pathSpecific = safeHttpUri(origin(endpoint) + "/.well-known/oauth-protected-resource"
            + (path == null || path.isEmpty() ? "" : path));
        URI originOnly = safeHttpUri(origin(endpoint) + "/.well-known/oauth-protected-resource");
        URI originResource = safeHttpUri(origin(endpoint));
        return java.util.stream.Stream.of(
            pathSpecific == null ? null : new ResourceCandidate(pathSpecific, endpoint),
            originOnly == null || originResource == null ? null : new ResourceCandidate(originOnly, originResource))
            .filter(Objects::nonNull).toList();
    }

    private URI advertisedLocation(URI endpoint, String value) {
        URI advertised = safeHttpUri(value);
        return advertised != null && (OAuthDiscoveryPolicy.advertisableFrom(endpoint, advertised)
            || origin(advertised).equals(origin(endpoint))) ? advertised : null;
    }

    public List<URI> authorizationMetadataLocations(URI issuer) {
        if (safeHttpUri(issuer.toString()) == null) return List.of();
        String path = issuer.getRawPath() == null ? "" : issuer.getRawPath();
        return java.util.stream.Stream.of(
            safeHttpUri(origin(issuer) + "/.well-known/oauth-authorization-server" + path),
            safeHttpUri(issuer.toString().replaceAll("/+$", "") + "/.well-known/openid-configuration"),
            safeHttpUri(origin(issuer) + "/.well-known/openid-configuration" + path))
            .filter(Objects::nonNull).distinct().toList();
    }

    public boolean matchesResource(URI actual, URI expected) {
        return actual.getScheme().equalsIgnoreCase(expected.getScheme())
            && actual.getHost().equalsIgnoreCase(expected.getHost())
            && port(actual) == port(expected)
            && Objects.equals(emptyPathAsSlash(actual), emptyPathAsSlash(expected))
            && Objects.equals(actual.getRawQuery(), expected.getRawQuery());
    }

    public boolean matchesIssuer(URI expected, Object advertised) {
        return expected.toString().equals(advertised);
    }

    public List<RegistrationMode> scanModes(boolean pkceS256, boolean dcr, boolean cimd) {
        if (!pkceS256) return List.of();
        return java.util.stream.Stream.of(dcr ? RegistrationMode.DCR : null,
            cimd ? RegistrationMode.CIMD : null).filter(Objects::nonNull).toList();
    }

    public Optional<String> challengeScope(Optional<String> challenge) {
        return challengeScope(challenge.stream().toList());
    }

    public Optional<String> challengeScope(List<String> challenges) {
        for (String challenge : challenges) {
            Matcher matcher = CHALLENGE_SCOPE.matcher(challenge);
            if (matcher.find() && !matcher.group(1).isBlank()) return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    public Optional<String> dcrClientId(Object id) {
        return id instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    public String dcrRequest(String clientName, URI redirectUri) {
        return dcrRequest(clientName, redirectUri, false);
    }

    public String dcrRequest(String clientName, URI redirectUri, boolean confidential) {
        Objects.requireNonNull(clientName);
        Objects.requireNonNull(redirectUri);
        String name = clientName.replace("\\", "\\\\").replace("\"", "\\\"");
        String redirect = redirectUri.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"client_name\":\"" + name + "\",\"redirect_uris\":[\"" + redirect + "\"]"
            + (confidential ? ",\"token_endpoint_auth_method\":\"client_secret_basic\"" : "") + "}";
    }

    public String dcrRequest(String clientName, URI redirectUri,
            String metadataField, String serializedJsonValue) {
        if (!List.of("client_uri", "logo_uri").contains(metadataField)
            || !jsonStringLiteral(serializedJsonValue)) throw new IllegalArgumentException("Invalid DCR metadata field");
        String ordinary = dcrRequest(clientName, redirectUri);
        return ordinary.substring(0, ordinary.length() - 1)
            + ",\"" + metadataField + "\":" + serializedJsonValue + "}";
    }

    private boolean jsonStringLiteral(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '"'
            || value.charAt(value.length() - 1) != '"') return false;
        for (int index = 1; index < value.length() - 1; index++) {
            char character = value.charAt(index);
            if (character == '"' || character < 0x20) return false;
            if (character != '\\') continue;
            if (++index >= value.length() - 1) return false;
            char escaped = value.charAt(index);
            if ("\"\\/bfnrt".indexOf(escaped) >= 0) continue;
            if (escaped != 'u' || index + 4 >= value.length() - 1) return false;
            for (int digit = 1; digit <= 4; digit++) {
                if (Character.digit(value.charAt(index + digit), 16) < 0) return false;
            }
            index += 4;
        }
        return true;
    }

    public static URI safeHttpUri(String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || (!uri.getScheme().equalsIgnoreCase("http")
                    && !uri.getScheme().equalsIgnoreCase("https"))) return null;
            return uri.normalize();
        } catch (URISyntaxException | NullPointerException invalid) {
            return null;
        }
    }

    private String emptyPathAsSlash(URI uri) {
        return uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    }

    private String origin(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (port == 80 && uri.getScheme().equalsIgnoreCase("http"))
            || (port == 443 && uri.getScheme().equalsIgnoreCase("https"));
        String authority = uri.getHost().toLowerCase();
        if (!defaultPort) authority += ":" + port;
        return uri.getScheme().toLowerCase() + "://" + authority;
    }

    private int port(URI uri) {
        return uri.getPort() == -1 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
    }
}
