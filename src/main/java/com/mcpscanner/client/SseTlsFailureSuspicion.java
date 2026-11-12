package com.mcpscanner.client;

import com.mcpscanner.core.connection.SseOpenException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class SseTlsFailureSuspicion {

    private static final List<Class<? extends Throwable>> NOT_TLS_FAILURES = List.of(
            ConnectException.class, HttpTimeoutException.class, UnresolvedAddressException.class,
            SseOpenException.class, SseStreamRejectedException.class);

    private SseTlsFailureSuspicion() {
    }

    static boolean suspected(String sseEndpoint, Throwable failure) {
        if (!isHttps(sseEndpoint)) return false;
        List<Throwable> chain = causesAndSuppressed(failure, Collections.newSetFromMap(new IdentityHashMap<>()))
                .toList();
        if (chain.stream().anyMatch(SSLException.class::isInstance)) return true;
        return chain.stream().noneMatch(SseTlsFailureSuspicion::isKnownNonTlsFailure)
                && chain.stream().anyMatch(IOException.class::isInstance);
    }

    private static boolean isKnownNonTlsFailure(Throwable failure) {
        return NOT_TLS_FAILURES.stream().anyMatch(type -> type.isInstance(failure));
    }

    private static boolean isHttps(String endpoint) {
        try {
            return "https".equalsIgnoreCase(URI.create(endpoint).getScheme());
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static Stream<Throwable> causesAndSuppressed(Throwable failure, Set<Throwable> visited) {
        if (failure == null || !visited.add(failure)) return Stream.empty();
        Stream<Throwable> suppressed = Stream.of(failure.getSuppressed())
                .flatMap(each -> causesAndSuppressed(each, visited));
        return Stream.concat(Stream.of(failure),
                Stream.concat(suppressed, causesAndSuppressed(failure.getCause(), visited)));
    }
}
