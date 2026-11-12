package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.HttpOrigin;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class UnreachableStreamWarnings {

    private final McpEventLog log;
    private final Set<HttpOrigin> warned = ConcurrentHashMap.newKeySet();

    UnreachableStreamWarnings(McpEventLog log) {
        this.log = log;
    }

    void note(HttpRequest request, Throwable failure) {
        Optional<Throwable> unreachable = connectionFailure(failure);
        HttpOrigin origin = HttpOrigin.of(request.httpService());
        if (unreachable.isPresent() && warned.add(origin)) {
            log.warn("GET event stream probe: could not open a stream to " + origin + " ("
                    + describe(unreachable.get()) + "). Event-stream probes use their own HTTP client, which does "
                    + "not present Burp's client TLS certificates or use Burp's upstream proxy, so the event stream "
                    + "on this host was not tested.");
        }
    }

    private static Optional<Throwable> connectionFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SSLException || cause instanceof ConnectException
                    || cause instanceof HttpConnectTimeoutException) {
                return Optional.of(cause);
            }
        }
        return Optional.empty();
    }

    private static String describe(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }
}
