package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.MetadataFetchFailures;
import com.mcpscanner.auth.oauth.discovery.ProtectedResourceDocument;
import com.mcpscanner.ui.detail.FieldGrid;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

final class AuthDiscoveryFields {

    private static final String NOT_FETCHED = "not fetched";
    private static final String FETCH_FAILED = "fetch failed: ";
    private static final String FETCHED_BY_FLOW = "issuer metadata, fetched during the OAuth flow";
    private static final String NO_REGISTRATION = "no dynamic registration";
    private static final String SEE_DOCUMENT = "shown below";
    private static final String EXPIRY_UNKNOWN = "not known";
    private static final String EXPIRY_PATTERN = "yyyy-MM-dd HH:mm:ss zzz";

    private final DateTimeFormatter expiryFormat;

    AuthDiscoveryFields(ZoneId zone) {
        expiryFormat = DateTimeFormatter.ofPattern(EXPIRY_PATTERN).withZone(zone);
    }

    FieldGrid of(AuthDiscoveryReport report) {
        Optional<DiscoveredMetadata> discovery = report.discovery();
        MetadataFetchFailures failures = discovery.map(DiscoveredMetadata::fetchFailures)
                .orElse(MetadataFetchFailures.none());
        return new FieldGrid()
                .addMonospaced("Protected resource metadata URL", orAbsence(
                        discovery.map(DiscoveredMetadata::protectedResource).map(ProtectedResourceDocument::url),
                        failures.protectedResourceMetadata()))
                .addMonospaced("Authorization server metadata source", authorizationServerSource(report))
                .addMonospaced("WWW-Authenticate challenge", orAbsence(
                        discovery.map(DiscoveredMetadata::wwwAuthenticateChallenge),
                        failures.wwwAuthenticateChallenge()))
                .addText("Registration response",
                        report.registrationResponseJson().map(json -> SEE_DOCUMENT).orElse(NO_REGISTRATION))
                .addText("Token expires", report.signIn().isEmpty() ? null : expiry(report.tokenExpiry()));
    }

    private static String authorizationServerSource(AuthDiscoveryReport report) {
        return report.discovery().map(found -> found.source().displayPath())
                .orElse(report.authorizationServerMetadataJson().isPresent() ? FETCHED_BY_FLOW : NOT_FETCHED);
    }

    private String expiry(Optional<Instant> expiresAt) {
        return expiresAt.map(expiryFormat::format).orElse(EXPIRY_UNKNOWN);
    }

    private static String orAbsence(Optional<?> value, String failureReason) {
        if (value.isPresent()) {
            return value.get().toString();
        }
        return failureReason == null ? NOT_FETCHED : FETCH_FAILED + failureReason;
    }
}
