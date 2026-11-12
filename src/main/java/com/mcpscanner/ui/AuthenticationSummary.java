package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.SignedInTokens;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class AuthenticationSummary {

    private static final DateTimeFormatter EXPIRY_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String NOT_REPORTED = "Not reported";
    private static final String NONE_REPORTED = "None reported";
    static final String TOKEN_EXPIRES = "Token expires";
    private static final String RENEWS = " · renews with the refresh token";
    private static final String SCOPES_REQUESTED = "Scopes requested";
    private static final String SCOPES_GRANTED = "Scopes granted";
    private static final List<String> SCOPE_LABELS = List.of(SCOPES_REQUESTED, SCOPES_GRANTED);

    private final String connectedAs;
    private final String method;
    private final URI issuer;
    private final String clientId;
    private final String registration;
    private final Map<String, List<String>> scopes;
    private final Supplier<Optional<Instant>> tokenExpiry;
    private final AuthStrategy credential;
    private final Optional<DcrRegistrations.Registration> clientManagement;

    private AuthenticationSummary(Builder builder) {
        this.connectedAs = builder.connectedAs;
        this.method = builder.method;
        this.clientManagement = builder.clientManagement;
        this.issuer = builder.issuer;
        this.clientId = builder.clientId;
        this.registration = builder.registration;
        this.scopes = Map.copyOf(builder.scopes);
        this.credential = builder.credential;
        this.tokenExpiry = builder.tokenExpiry != null || !renewable()
                ? builder.tokenExpiry
                : () -> token().flatMap(AccessToken::expiresAt);
    }

    static Builder builder(String method) {
        return new Builder(method);
    }

    Optional<DcrRegistrations.Registration> clientManagement() {
        return clientManagement;
    }

    Optional<AccessToken> token() {
        return credential == null ? Optional.empty() : credential.currentToken();
    }

    boolean renewable() {
        return credential != null && credential.supportsRefresh();
    }

    boolean renew() {
        return renewable() && credential.renew();
    }

    Map<String, String> rows(Clock clock) {
        Map<String, String> rows = new LinkedHashMap<>();
        putIfPresent(rows, "Connected as", connectedAs);
        rows.put("Method", method);
        putIfPresent(rows, "Issuer", issuer);
        putIfPresent(rows, "Client ID", clientId);
        putIfPresent(rows, "Registration", registration);
        SCOPE_LABELS.stream().filter(scopes::containsKey).forEach(label ->
                rows.put(label, scopes.get(label).isEmpty() ? NONE_REPORTED : String.join(" ", scopes.get(label))));
        if (tokenExpiry != null) {
            rows.put(TOKEN_EXPIRES, tokenExpiry.get().map(expiry -> describeExpiry(expiry, clock)).orElse(NOT_REPORTED)
                    + (holdsRefreshToken() ? RENEWS : ""));
        }
        return rows;
    }

    Optional<String> refreshToken() {
        return credential == null ? Optional.empty()
                : credential.signedInTokens().flatMap(SignedInTokens::refreshToken);
    }

    private boolean holdsRefreshToken() {
        return refreshToken().isPresent();
    }

    private static void putIfPresent(Map<String, String> rows, String label, Object value) {
        if (value != null) {
            rows.put(label, value.toString());
        }
    }

    private static String describeExpiry(Instant expiry, Clock clock) {
        String time = EXPIRY_TIME.format(expiry.atZone(clock.getZone()));
        Duration remaining = Duration.between(clock.instant(), expiry);
        if (remaining.isNegative()) {
            return time + " (expired)";
        }
        return time + " (in " + Math.max(1, remaining.toMinutes()) + " min)";
    }

    static final class Builder {
        private final String method;
        private String connectedAs;
        private Optional<DcrRegistrations.Registration> clientManagement = Optional.empty();
        private URI issuer;
        private String clientId;
        private String registration;
        private final Map<String, List<String>> scopes = new LinkedHashMap<>();
        private Supplier<Optional<Instant>> tokenExpiry;
        private AuthStrategy credential;

        private Builder(String method) {
            this.method = method;
        }

        Builder connectedAs(String user) {
            this.connectedAs = user + " (primary user)";
            return this;
        }

        Builder clientManagement(Optional<DcrRegistrations.Registration> registration) {
            this.clientManagement = registration;
            return this;
        }

        Builder issuer(URI issuer) {
            this.issuer = issuer;
            return this;
        }

        Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        Builder registration(String registration) {
            this.registration = registration;
            return this;
        }

        Builder grantedScopes(List<String> scopes) {
            return scopes(SCOPES_GRANTED, scopes);
        }

        Builder requestedScopes(List<String> scopes) {
            return scopes(SCOPES_REQUESTED, scopes);
        }

        private Builder scopes(String label, List<String> scopes) {
            this.scopes.put(label, List.copyOf(scopes));
            return this;
        }

        Builder tokenExpiry(Supplier<Optional<Instant>> tokenExpiry) {
            this.tokenExpiry = tokenExpiry;
            return this;
        }

        Builder credential(AuthStrategy credential) {
            this.credential = credential;
            return this;
        }

        AuthenticationSummary build() {
            return new AuthenticationSummary(this);
        }
    }
}
