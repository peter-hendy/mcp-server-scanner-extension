package com.mcpscanner.proxy;

import com.mcpscanner.client.JdkProtocolHttpTransport;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.config.RedirectMode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public record ProxySettings(HttpClient httpClient,
                            ProtocolHttpTransport upstreamTransport,
                            Duration responseTimeout,
                            ClientIdentityProvider identityProvider,
                            Supplier<RedirectMode> redirectMode,
                            LongSupplier postBodyLimit,
                            LongSupplier pendingResponseBudget,
                            BooleanSupplier answerServerPings) {

    public static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    public ProxySettings {
        Objects.requireNonNull(httpClient, "httpClient");
        Objects.requireNonNull(upstreamTransport, "upstreamTransport");
        Objects.requireNonNull(identityProvider, "identityProvider");
        Objects.requireNonNull(redirectMode, "redirectMode");
        Objects.requireNonNull(postBodyLimit, "postBodyLimit");
        Objects.requireNonNull(pendingResponseBudget, "pendingResponseBudget");
        Objects.requireNonNull(answerServerPings, "answerServerPings");
        if (responseTimeout.isZero() || responseTimeout.isNegative()) {
            throw new IllegalArgumentException("Positive response timeout required");
        }
    }

    public ProxySettings(HttpClient httpClient,
                         Duration responseTimeout,
                         ClientIdentityProvider identityProvider,
                         Supplier<RedirectMode> redirectMode,
                         LongSupplier postBodyLimit,
                         LongSupplier pendingResponseBudget,
                         BooleanSupplier answerServerPings) {
        this(httpClient, new JdkProtocolHttpTransport(Objects.requireNonNull(httpClient, "httpClient")),
                responseTimeout, identityProvider, redirectMode, postBodyLimit, pendingResponseBudget,
                answerServerPings);
    }
}
