package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class OAuthChallengeParser {
    private static final String TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    private static final Pattern PARAMETER = Pattern.compile("^(" + TOKEN + ")\\s*=\\s*(.*)$");
    private static final Pattern SCHEME = Pattern.compile("^(" + TOKEN + ")(?:[ \\t]+(.*))?$");

    public Optional<BearerChallenge> parse(List<String> headers) {
        var parameters = new HashMap<String, String>();
        boolean bearer = false;
        boolean found = false;
        int length = 0;
        boolean firstPart = true;
        for (String header : headers) {
            if (header == null || (length += header.length()) > 16384 || header.chars().anyMatch(c -> c < 0x20 && c != '\t' || c == 0x7f)) throw invalid();
            for (String part : split(header)) {
                if (part.isEmpty()) continue;
                var parameter = PARAMETER.matcher(part);
                if (parameter.matches()) {
                    if (firstPart) throw invalid();
                    if (bearer) add(parameters, parameter.group(1), parameter.group(2));
                    continue;
                }
                firstPart = false;
                var scheme = SCHEME.matcher(part);
                if (!scheme.matches()) throw invalid();
                bearer = scheme.group(1).equalsIgnoreCase("Bearer");
                if (bearer) {
                    found = true;
                    if (scheme.group(2) != null) {
                        var first = PARAMETER.matcher(scheme.group(2));
                        if (!first.matches()) throw invalid();
                        add(parameters, first.group(1), first.group(2));
                    }
                }
            }
        }
        if (!found) return Optional.empty();
        URI metadata = null;
        if (parameters.containsKey("resource_metadata")) {
            try { metadata = URI.create(parameters.get("resource_metadata")); }
            catch (IllegalArgumentException failure) { throw invalid(); }
            if (!OAuthDiscoveryPolicy.validLocation(metadata)) throw invalid();
        }
        String scope = parameters.get("scope");
        return Optional.of(new BearerChallenge(metadata, parameters.get("error"), scope == null ? Set.of() : ScopeValues.parse(scope), scope != null));
    }

    private List<String> split(String header) {
        var parts = new ArrayList<String>();
        boolean quoted = false;
        boolean escaped = false;
        int start = 0;
        for (int index = 0; index < header.length(); index++) {
            char character = header.charAt(index);
            if (escaped) escaped = false;
            else if (quoted && character == '\\') escaped = true;
            else if (character == '"') quoted = !quoted;
            else if (!quoted && character == ',') {
                parts.add(header.substring(start, index).trim());
                start = index + 1;
            }
        }
        if (quoted || escaped) throw invalid();
        parts.add(header.substring(start).trim());
        return parts;
    }

    private void add(Map<String, String> parameters, String name, String raw) {
        name = name.toLowerCase(Locale.ROOT);
        String value;
        if (raw.startsWith("\"")) {
            if (raw.length() < 2 || !raw.endsWith("\"")) throw invalid();
            var decoded = new StringBuilder();
            for (int index = 1; index < raw.length() - 1; index++) {
                char character = raw.charAt(index);
                if (character == '\\') {
                    if (++index >= raw.length() - 1) throw invalid();
                    character = raw.charAt(index);
                } else if (character == '"') throw invalid();
                decoded.append(character);
            }
            value = decoded.toString();
        } else {
            if (!raw.matches(TOKEN) && !unquotedMetadataLocation(name, raw)) throw invalid();
            value = raw;
        }
        if (parameters.putIfAbsent(name, value) != null || parameters.size() > 32) throw invalid();
    }

    private boolean unquotedMetadataLocation(String name, String value) {
        if (!name.equals("resource_metadata")) return false;
        try {
            URI location = URI.create(value);
            return "https".equalsIgnoreCase(location.getScheme()) && OAuthDiscoveryPolicy.validLocation(location);
        } catch (IllegalArgumentException failure) { return false; }
    }

    private OAuthPolicyException invalid() { return new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_CHALLENGE); }
}
