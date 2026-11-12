package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class AbandonedClientLog {

    static final int LISTED_LIMIT = 20;

    private final McpEventLog eventLog;
    private final Map<String, Integer> byOrigin = new TreeMap<>();
    private final Map<String, Integer> byCause = new TreeMap<>();
    private int total;

    public AbandonedClientLog(McpEventLog eventLog) {
        this.eventLog = eventLog;
    }

    public void leftByRegistration(HttpRequestResponse registration, String cause) {
        HttpUris.parse(registration.request().url()).ifPresent(endpoint ->
                JsonDocument.fromSuccessfulResponse(registration).ifPresent(document ->
                        ClientManagement.from(document, endpoint).ifPresent(client ->
                                left(client.registrationClientUri(), document.text("client_id"), cause))));
    }

    void left(ClientManagement client, String cause) {
        left(client.registrationClientUri(), Optional.empty(), cause);
    }

    public synchronized void summarise() {
        if (total > 0) {
            eventLog.error("Registered OAuth clients left needing manual deletion: " + total
                    + "; by origin: " + tally(byOrigin) + "; by cause: " + tally(byCause));
        }
    }

    private synchronized void left(URI registrationClientUri, Optional<String> clientId, String cause) {
        String origin = HttpUris.origin(registrationClientUri);
        total++;
        byOrigin.merge(origin, 1, Integer::sum);
        byCause.merge(cause, 1, Integer::sum);
        if (total <= LISTED_LIMIT) {
            eventLog.error("Registered OAuth client left on " + origin + " (" + cause
                    + "); delete it manually: registration_client_uri " + registrationClientUri
                    + clientId.map(id -> ", client_id " + id).orElse(""));
        } else if (total == LISTED_LIMIT + 1) {
            eventLog.error("More registered OAuth clients were left behind than are listed; "
                    + "the summary at unload counts them all");
        }
    }

    private static String tally(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(count -> count.getKey() + " " + count.getValue())
                .collect(Collectors.joining(", "));
    }
}
