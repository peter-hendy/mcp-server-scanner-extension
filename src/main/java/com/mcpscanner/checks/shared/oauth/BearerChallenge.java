package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record BearerChallenge(Optional<URI> resourceMetadata, Optional<String> scope) {

    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final Pattern RESOURCE_METADATA =
            Pattern.compile("(?i)(?:^|[,\\s])resource_metadata\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern SCOPE = Pattern.compile("(?i)(?:^|[,\\s])scope\\s*=\\s*\"([^\"]*)\"");

    public static BearerChallenge none() {
        return new BearerChallenge(Optional.empty(), Optional.empty());
    }

    public static BearerChallenge of(HttpResponse response) {
        return response == null ? none() : from(ResponseHeaders.values(response, WWW_AUTHENTICATE));
    }

    public static BearerChallenge from(List<String> wwwAuthenticateValues) {
        List<String> bearer = wwwAuthenticateValues.stream()
                .filter(value -> value.stripLeading().regionMatches(true, 0, "Bearer", 0, 6))
                .toList();
        return new BearerChallenge(
                firstGroup(bearer, RESOURCE_METADATA).flatMap(HttpUris::parse),
                firstGroup(bearer, SCOPE).map(String::trim).filter(scope -> !scope.isEmpty()));
    }

    private static Optional<String> firstGroup(List<String> values, Pattern pattern) {
        return values.stream()
                .map(pattern::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst();
    }
}
