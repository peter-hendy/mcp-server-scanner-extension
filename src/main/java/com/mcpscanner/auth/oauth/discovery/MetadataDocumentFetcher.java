package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.BurpMetadataClient;
import com.mcpscanner.auth.oauth.OAuthUrlValidator;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.core.oauth.MetadataRedirectPolicy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.HttpOrigin;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

final class MetadataDocumentFetcher {
    private final BurpMetadataClient metadataClient;
    private final SuspiciousDestinationGate gate;
    private final SuspiciousDestinationGate redirectGate;
    private final OAuthUrlValidator urlValidator = new OAuthUrlValidator();
    private final McpEventLog eventLog;

    MetadataDocumentFetcher(Http http, SuspiciousDestinationGate gate, McpEventLog eventLog) {
        this.metadataClient = new BurpMetadataClient(new BurpHttpRequestSender(http));
        this.gate = gate;
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
        this.redirectGate = DefaultSuspiciousDestinationGate.toleratingCrossOrigin(this.eventLog);
    }

    Optional<String> fetch(URI initialUrl, FetchPurpose purpose) {
        return fetch(initialUrl, purpose, false, reason -> { });
    }

    Optional<String> fetchOk(URI initialUrl, FetchPurpose purpose) {
        return fetchOk(initialUrl, purpose, reason -> { });
    }

    Optional<String> fetchOk(URI initialUrl, FetchPurpose purpose, Consumer<String> onFailure) {
        return fetch(initialUrl, purpose, true, onFailure);
    }

    private Optional<String> fetch(URI initialUrl, FetchPurpose purpose, boolean requireOk,
                                   Consumer<String> onFailure) {
        try {
            MetadataRedirectPolicy.Response response = metadataClient.fetch(
                    initialUrl, url -> validateDestination(initialUrl, url, purpose));
            if ((requireOk ? response.status() != 200 : response.status() / 100 != 2)
                    || response.body() == null) {
                warnFetchFailure(initialUrl, purpose, response.status());
                onFailure.accept(failureReason(response));
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (IllegalArgumentException rejected) {
            eventLog.warn("OAuth metadata fetch rejected: " + rejected.getMessage());
            onFailure.accept("rejected: " + rejected.getMessage());
            return Optional.empty();
        }
    }

    private static String failureReason(MetadataRedirectPolicy.Response response) {
        if (response.status() == 0) {
            return "no response";
        }
        return response.body() == null && response.status() / 100 == 2
                ? "status " + response.status() + ", unreadable body" : "status " + response.status();
    }

    private void warnFetchFailure(URI url, FetchPurpose purpose, int status) {
        String label = switch (purpose.label()) {
            case "Authorization Server Metadata (.well-known)" -> "AS well-known fetch failed";
            case "Protected Resource Metadata (.well-known)" -> "PRM well-known fetch failed";
            default -> "PRM/AS document fetch failed";
        };
        eventLog.warn(label + " for " + url + ": status " + status);
    }

    private boolean validateDestination(URI initialUrl, URI url, FetchPurpose purpose) {
        SuspiciousDestinationGate.Decision decision = gate.evaluate(url, purpose);
        if (decision.isDenied()) {
            eventLog.warn("OAuth metadata fetch blocked by gate: " + url);
            return false;
        }
        if (url.equals(initialUrl)
                || (HttpOrigin.sameOrigin(initialUrl, url) && isExplicitLocalOrigin(initialUrl))) {
            return true;
        }
        boolean allowed = redirectGate.evaluate(url, purpose).isAllowed();
        if (!allowed) {
            eventLog.warn("OAuth metadata redirect blocked by policy: " + url);
        }
        return allowed;
    }

    private boolean isExplicitLocalOrigin(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
        if (normalizedHost.equals("localhost")) {
            return true;
        }
        if (!isIpLiteral(normalizedHost)) {
            return false;
        }
        return urlValidator.classify(uri).filter(classification ->
                classification.equals(OAuthUrlValidator.CLASSIFICATION_LOOPBACK)
                        || classification.equals(OAuthUrlValidator.CLASSIFICATION_PRIVATE)).isPresent();
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
