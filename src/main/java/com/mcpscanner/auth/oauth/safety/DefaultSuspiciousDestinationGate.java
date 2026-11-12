package com.mcpscanner.auth.oauth.safety;

import com.mcpscanner.auth.oauth.OAuthUrlValidator;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production {@link SuspiciousDestinationGate}. Resolves the destination, classifies it via
 * {@link OAuthUrlValidator#classifyAll(URI, HostResolver, URI)}, applies the per-connection
 * decision cache, and delegates tolerated-with-prompt classifications to a
 * {@link SuspiciousDestinationConfirmer}.
 *
 * <p>Hard-block classifications (invalid scheme, unresolvable, etc.) deny without ever
 * prompting the user.
 */
public final class DefaultSuspiciousDestinationGate implements SuspiciousDestinationGate {

    private static final Set<String> HARD_BLOCK_CLASSIFICATIONS = Set.of(
            OAuthUrlValidator.CLASSIFICATION_MISSING_SCHEME_OR_HOST,
            OAuthUrlValidator.CLASSIFICATION_INVALID_SCHEME,
            OAuthUrlValidator.CLASSIFICATION_INVALID_PORT,
            OAuthUrlValidator.CLASSIFICATION_UNRESOLVABLE);

    private final OAuthUrlValidator validator;
    private final HostResolver resolver;
    private final SuspiciousDestinationConfirmer confirmer;
    private final McpEventLog eventLog;
    // Shared across SwingWorker connect, token exchange, and browser-launch threads; needs concurrent access.
    private final Map<CacheKey, Decision> cache = new ConcurrentHashMap<>();
    private final OriginApprovals approvals = new OriginApprovals();

    private record CacheKey(String destination, URI mcpOrigin) {}

    public DefaultSuspiciousDestinationGate(OAuthUrlValidator validator,
                                            HostResolver resolver,
                                            SuspiciousDestinationConfirmer confirmer,
                                            McpEventLog eventLog) {
        this.validator = validator;
        this.resolver = resolver;
        this.confirmer = confirmer;
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
    }

    public static DefaultSuspiciousDestinationGate prompting(SuspiciousDestinationConfirmer confirmer,
                                                             HostResolver resolver,
                                                             McpEventLog eventLog) {
        return new DefaultSuspiciousDestinationGate(new OAuthUrlValidator(), resolver, confirmer, eventLog);
    }

    public static DefaultSuspiciousDestinationGate denyingSuspicious(McpEventLog eventLog) {
        return withDefaultResolver(SuspiciousDestinationConfirmer.alwaysDeny(), eventLog);
    }

    public static DefaultSuspiciousDestinationGate allowingSuspicious(McpEventLog eventLog) {
        return withDefaultResolver(SuspiciousDestinationConfirmer.alwaysAllow(), eventLog);
    }

    public static DefaultSuspiciousDestinationGate toleratingCrossOrigin(McpEventLog eventLog) {
        return withDefaultResolver(DefaultSuspiciousDestinationGate::isOnlyCrossOrigin, eventLog);
    }

    private static DefaultSuspiciousDestinationGate withDefaultResolver(SuspiciousDestinationConfirmer confirmer,
                                                                        McpEventLog eventLog) {
        return prompting(confirmer, HostResolver.defaultResolver(), eventLog);
    }

    private static boolean isOnlyCrossOrigin(Reason reason) {
        return reason.classifications().stream().allMatch(OAuthUrlValidator.CLASSIFICATION_CROSS_ORIGIN::equals);
    }

    @Override
    public Decision evaluate(URI url, FetchPurpose purpose) {
        CacheKey cacheKey = cacheKey(url, purpose);
        Decision cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Decision decision = classifyAndDecide(url, purpose);
        cache.put(cacheKey, decision);
        log(decision, url, purpose);
        return decision;
    }

    public void reset() {
        cache.clear();
        approvals.clear();
    }

    private Decision classifyAndDecide(URI url, FetchPurpose purpose) {
        OAuthUrlValidator.Classification classification = validator.classifyAll(url, resolver, originOf(purpose));
        List<String> codes = classification.codes();
        if (codes.isEmpty()) {
            return Decision.allow();
        }
        if (hasHardBlock(codes)) {
            return Decision.deny(reasonFor(url, classification, purpose, hardBlockMessage(codes, url)));
        }
        String origin = destinationOrigin(url);
        if (approvals.covers(origin, codes)) {
            return Decision.allow();
        }
        Reason reason = reasonFor(url, classification, purpose, suspiciousMessage(codes, url));
        if (!userApproves(reason)) {
            return Decision.deny(reason);
        }
        approvals.record(origin, codes);
        return Decision.allow();
    }

    private boolean userApproves(Reason reason) {
        try {
            return confirmer.confirm(reason);
        } catch (RuntimeException ex) {
            warn("SuspiciousDestinationConfirmer threw " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage() + " — treating as deny");
            return false;
        }
    }

    private Reason reasonFor(URI url,
                             OAuthUrlValidator.Classification classification,
                             FetchPurpose purpose,
                             String message) {
        return new Reason(url, classification.resolvedAddress(), classification.codes(), purpose, message);
    }

    private static boolean hasHardBlock(List<String> codes) {
        for (String code : codes) {
            if (HARD_BLOCK_CLASSIFICATIONS.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static String hardBlockMessage(List<String> codes, URI url) {
        return "Blocked OAuth fetch to " + url + " (" + String.join(", ", codes) + ")";
    }

    private static String suspiciousMessage(List<String> codes, URI url) {
        return "Suspicious OAuth fetch destination " + url + " (" + String.join(", ", codes) + ")";
    }

    private static CacheKey cacheKey(URI url, FetchPurpose purpose) {
        return new CacheKey(destinationOrigin(url), originOf(purpose));
    }

    private static String destinationOrigin(URI url) {
        if (url == null) {
            return "<null>";
        }
        if (url.getScheme() == null || url.getRawAuthority() == null) {
            return url.toString().toLowerCase(Locale.ROOT);
        }
        return (url.getScheme() + "://" + url.getRawAuthority()).toLowerCase(Locale.ROOT);
    }

    private static URI originOf(FetchPurpose purpose) {
        URI endpoint = purpose.mcpEndpoint();
        if (endpoint == null) {
            return null;
        }
        try {
            return new URI(endpoint.getScheme(), null, endpoint.getHost(), endpoint.getPort(), "/", null, null);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private void log(Decision decision, URI url, FetchPurpose purpose) {
        if (eventLog == null) {
            return;
        }
        if (decision.isAllowed() && decision.reason() == null) {
            return; // clean URL — no need to spam the log.
        }
        String prefix = decision.isAllowed()
                ? "OAuth destination approved by user"
                : "OAuth destination denied";
        Reason reason = decision.reason();
        String classification = reason != null
                ? String.join(", ", reason.classifications())
                : "<unknown>";
        eventLog.warn(prefix + " — " + purpose.label() + " → " + url
                + " [" + classification + "]");
    }

    private void warn(String message) {
        eventLog.warn(message);
    }
}
