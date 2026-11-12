package com.mcpscanner.client;

import java.net.URI;
import java.util.Map;

interface ConnectionAttemptSink {

    URI endpointFor(ConnectionAttempt attempt);

    void rememberCredentials(ConnectionAttempt attempt, Map<String, String> requestHeaders);

    void recordAdmission(ConnectionAttempt attempt, String body, URI reachedUri);

    void recordSseMessageEndpoint(ConnectionAttempt attempt, URI messageUrl);

    void recordSseAdmission(ConnectionAttempt attempt, String json);
}
