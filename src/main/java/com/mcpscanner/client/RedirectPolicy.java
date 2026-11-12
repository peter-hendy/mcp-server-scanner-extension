package com.mcpscanner.client;

import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.HttpOrigin;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class RedirectPolicy {

    public static final int MAX_REDIRECTS = 5;

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final McpEventLog eventLog;
    private volatile Supplier<RedirectMode> modeSupplier;

    public RedirectPolicy(McpEventLog eventLog) {
        this(eventLog, () -> RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
    }

    public RedirectPolicy(McpEventLog eventLog, Supplier<RedirectMode> modeSupplier) {
        this.eventLog = eventLog;
        setModeSupplier(modeSupplier);
    }

    public void setModeSupplier(Supplier<RedirectMode> modeSupplier) {
        this.modeSupplier = Objects.requireNonNull(modeSupplier);
    }

    public RedirectMode mode() {
        return modeSupplier.get();
    }

    public Optional<RedirectHop> nextHop(int status, String method, URI source, Optional<String> location) {
        if (!isRedirect(status)) return Optional.empty();
        Optional<URI> target = location.flatMap(value -> resolve(source, value))
                .filter(resolved -> allowed(status, source, resolved));
        target.ifPresent(resolved -> warnIfCrossOrigin("allowed", source, resolved));
        return target.map(resolved -> hop(status, method, resolved));
    }

    public void warnIfCrossOriginFollowed(URI sent, URI received) {
        if (received != null) warnIfCrossOrigin("followed", sent, received);
    }

    public static boolean isRedirect(int status) {
        return REDIRECT_STATUSES.contains(status);
    }

    private boolean allowed(int status, URI source, URI target) {
        return switch (mode()) {
            case METHOD_PRESERVING_SAME_ORIGIN -> isMethodPreserving(status) && HttpOrigin.sameOrigin(source, target);
            case SAME_ORIGIN -> HttpOrigin.sameOrigin(source, target);
            case ALWAYS -> true;
        };
    }

    private static boolean isMethodPreserving(int status) {
        return status == 307 || status == 308;
    }

    private static RedirectHop hop(int status, String method, URI target) {
        boolean switchToGet = switchesToGet(status, method);
        return new RedirectHop(target, switchToGet ? "GET" : method, switchToGet);
    }

    private static boolean switchesToGet(int status, String method) {
        return status == 303 || ((status == 301 || status == 302)
                && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method));
    }

    private static Optional<URI> resolve(URI base, String location) {
        try {
            return Optional.of(base.resolve(location)).filter(RedirectPolicy::isFollowableTarget);
        } catch (IllegalArgumentException invalidLocation) {
            return Optional.empty();
        }
    }

    private static boolean isFollowableTarget(URI target) {
        return target.getHost() != null && target.getRawUserInfo() == null
                && target.getRawFragment() == null
                && ("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()));
    }

    private void warnIfCrossOrigin(String outcome, URI source, URI target) {
        if (!HttpOrigin.sameOrigin(source, target)) {
            eventLog.warn("Cross-origin redirect " + outcome + ": " + origin(source) + " -> " + origin(target));
        }
    }

    private static String origin(URI uri) {
        return HttpOrigin.parse(uri).map(HttpOrigin::toString).orElse(String.valueOf(uri));
    }
}
