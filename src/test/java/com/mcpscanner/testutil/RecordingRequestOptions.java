package com.mcpscanner.testutil;

import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;

import java.time.Duration;
import java.util.Optional;

public final class RecordingRequestOptions implements RequestOptions {

    private final Optional<RedirectionMode> redirectionMode;
    private final Optional<Duration> responseTimeout;
    private final Optional<HttpMode> httpMode;
    private final Optional<String> connectionId;

    public RecordingRequestOptions() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private RecordingRequestOptions(Optional<RedirectionMode> redirectionMode, Optional<Duration> responseTimeout,
                                    Optional<HttpMode> httpMode, Optional<String> connectionId) {
        this.redirectionMode = redirectionMode;
        this.responseTimeout = responseTimeout;
        this.httpMode = httpMode;
        this.connectionId = connectionId;
    }

    public static RecordingRequestOptions recorded(RequestOptions options) {
        return (RecordingRequestOptions) options;
    }

    public Optional<RedirectionMode> redirectionMode() {
        return redirectionMode;
    }

    public Optional<Duration> responseTimeout() {
        return responseTimeout;
    }

    public Optional<HttpMode> httpMode() {
        return httpMode;
    }

    public Optional<String> connectionId() {
        return connectionId;
    }

    @Override
    public RequestOptions withRedirectionMode(RedirectionMode redirectionMode) {
        return new RecordingRequestOptions(Optional.of(redirectionMode), responseTimeout, httpMode, connectionId);
    }

    @Override
    public RequestOptions withResponseTimeout(long timeoutMillis) {
        return new RecordingRequestOptions(redirectionMode, Optional.of(Duration.ofMillis(timeoutMillis)), httpMode,
                connectionId);
    }

    @Override
    public RequestOptions withHttpMode(HttpMode httpMode) {
        return new RecordingRequestOptions(redirectionMode, responseTimeout, Optional.of(httpMode), connectionId);
    }

    @Override
    public RequestOptions withConnectionId(String connectionId) {
        return new RecordingRequestOptions(redirectionMode, responseTimeout, httpMode, Optional.of(connectionId));
    }

    @Override
    public RequestOptions withUpstreamTLSVerification() {
        return this;
    }

    @Override
    public RequestOptions withServerNameIndicator(String serverNameIndicator) {
        return this;
    }
}
