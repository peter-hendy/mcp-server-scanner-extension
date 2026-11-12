package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;

import java.util.Set;
import java.util.TreeSet;

final class CredentialPresence {

    private static final Set<String> CREDENTIAL_HEADERS = caseInsensitive("Authorization");

    private CredentialPresence() {}

    static boolean configured(AuthStrategy strategy, HttpRequest seed) {
        return !strategy.contributedHeaderNames().isEmpty()
                || seed.headers().stream().anyMatch(header -> CREDENTIAL_HEADERS.contains(header.name()));
    }

    private static Set<String> caseInsensitive(String... names) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(Set.of(names));
        return set;
    }
}
