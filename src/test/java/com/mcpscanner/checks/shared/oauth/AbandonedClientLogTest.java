package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static org.assertj.core.api.Assertions.assertThat;

class AbandonedClientLogTest {

    private static final String ENDPOINT = "https://auth.example.com/register";
    private static final String MANAGED = "https://auth.example.com/register/c1";

    private final McpEventLog eventLog = new McpEventLog(null);
    private final AbandonedClientLog abandoned = new AbandonedClientLog(eventLog);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    static HttpRequestResponse registration(String endpoint, String rawResponse) {
        return HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST").withBody("{\"redirect_uris\":[]}"),
                HttpResponse.httpResponse(rawResponse));
    }

    static String managed(String uri, String clientId) {
        return "{\"client_id\":\"" + clientId + "\",\"registration_client_uri\":\"" + uri + "\","
                + "\"registration_access_token\":\"rat\"}";
    }

    private List<String> lines() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    @Test
    void namesTheOriginManagementUriAndClientIdOfAClientARegistrationLeftBehind() {
        abandoned.leftByRegistration(registration(ENDPOINT, json(201, managed(MANAGED, "c1"))), "cleanup queue full");

        assertThat(eventLog.snapshot()).singleElement().satisfies(entry -> {
            assertThat(entry.level()).isEqualTo(McpEventLog.Level.ERROR);
            assertThat(entry.message()).isEqualTo("Registered OAuth client left on https://auth.example.com "
                    + "(cleanup queue full); delete it manually: registration_client_uri " + MANAGED
                    + ", client_id c1");
        });
    }

    @Test
    void namesTheManagementUriOfARetainedClient() {
        abandoned.left(new ClientManagement(URI.create(MANAGED), "rat"), "retained at unload");

        assertThat(lines()).containsExactly("Registered OAuth client left on https://auth.example.com "
                + "(retained at unload); delete it manually: registration_client_uri " + MANAGED);
    }

    @Test
    void ignoresARegistrationThatLeftNoDeletableClient() {
        abandoned.leftByRegistration(registration(ENDPOINT, json(400, managed(MANAGED, "c1"))), "cleanup queue full");

        assertThat(lines()).isEmpty();
    }

    @Test
    void listsAtMostABoundedNumberOfClientsThenSaysTheRestAreCountedOnly() {
        IntStream.range(0, AbandonedClientLog.LISTED_LIMIT + 5).forEach(client ->
                abandoned.left(new ClientManagement(URI.create(MANAGED + client), "rat"), "retained at unload"));

        assertThat(lines()).hasSize(AbandonedClientLog.LISTED_LIMIT + 1)
                .last().isEqualTo("More registered OAuth clients were left behind than are listed; "
                        + "the summary at unload counts them all");
    }

    @Test
    void summarisesEveryClientLeftBehindByOriginAndCause() {
        IntStream.range(0, AbandonedClientLog.LISTED_LIMIT + 1).forEach(client ->
                abandoned.left(new ClientManagement(URI.create(MANAGED + client), "rat"), "retained at unload"));
        abandoned.leftByRegistration(registration("https://other.example.com/register",
                json(201, managed("https://other.example.com/register/c9", "c9"))), "cleanup queue full");

        abandoned.summarise();

        assertThat(lines()).last().isEqualTo("Registered OAuth clients left needing manual deletion: 22; "
                + "by origin: https://auth.example.com 21, https://other.example.com 1; "
                + "by cause: cleanup queue full 1, retained at unload 21");
    }

    @Test
    void summarisesNothingWhenNoClientWasLeftBehind() {
        abandoned.summarise();

        assertThat(lines()).isEmpty();
    }
}
