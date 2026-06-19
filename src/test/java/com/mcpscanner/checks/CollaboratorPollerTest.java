package com.mcpscanner.checks;

import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionId;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import com.mcpscanner.checks.ToolArgRcePayloads.RcePayloadTemplate;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollaboratorPollerTest {

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    private CollaboratorClient client;
    private List<AuditIssue> reported;
    private CollaboratorPoller poller;

    @BeforeEach
    void setUp() {
        client = mock(CollaboratorClient.class);
        reported = new CopyOnWriteArrayList<>();
    }

    private CollaboratorPoller newPoller(Supplier<Collaborator> collaboratorSupplier) {
        return new CollaboratorPoller(collaboratorSupplier, mock(McpEventLog.class),
                reported::add, Duration.ofMillis(20));
    }

    private CollaboratorPoller pollerWithClient() {
        Collaborator collaborator = mock(Collaborator.class);
        lenient().when(collaborator.createClient()).thenReturn(client);
        return newPoller(() -> collaborator);
    }

    private CollaboratorPoller clockedPoller(MutableClock clock) {
        Collaborator collaborator = mock(Collaborator.class);
        lenient().when(collaborator.createClient()).thenReturn(client);
        return new CollaboratorPoller(() -> collaborator, mock(McpEventLog.class),
                reported::add, Duration.ofMillis(20), clock);
    }

    private static final class MutableClock implements java.util.function.LongSupplier {
        private long now;

        MutableClock(long start) {
            this.now = start;
        }

        void advance(long millis) {
            now += millis;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static RcePayloadTemplate payload() {
        return ToolArgRcePayloads.all().get(0);
    }

    private static ProbeContext context() {
        return new ProbeContext("https://host:8080", "format_quote", "format",
                payload(), "abc.oastify.example", mock(HttpRequestResponse.class));
    }

    private static Interaction interaction(String id, InteractionType type) {
        Interaction interaction = mock(Interaction.class);
        InteractionId interactionId = mock(InteractionId.class);
        lenient().when(interactionId.toString()).thenReturn(id);
        lenient().when(interaction.id()).thenReturn(interactionId);
        lenient().when(interaction.type()).thenReturn(type);
        return interaction;
    }

    @Test
    void sharedClientIsCreatedOnceAndReused() {
        Collaborator collaborator = mock(Collaborator.class);
        when(collaborator.createClient()).thenReturn(client);
        poller = newPoller(() -> collaborator);

        CollaboratorClient first = poller.sharedClient();
        CollaboratorClient second = poller.sharedClient();

        assertThat(first).isSameAs(client);
        assertThat(second).isSameAs(client);
        org.mockito.Mockito.verify(collaborator, org.mockito.Mockito.times(1)).createClient();
    }

    @Test
    void sharedClientReturnsNullWhenCollaboratorUnavailable() {
        poller = newPoller(() -> null);

        assertThat(poller.sharedClient()).isNull();
    }

    @Test
    void sharedClientReturnsNullWhenCreateThrows() {
        Collaborator collaborator = mock(Collaborator.class);
        when(collaborator.createClient()).thenThrow(new IllegalStateException("disabled"));
        poller = newPoller(() -> collaborator);

        assertThat(poller.sharedClient()).isNull();
    }

    @Test
    void reportsIssueWhenMatchingInteractionArrives() {
        poller = pollerWithClient();
        poller.sharedClient();
        poller.register("iid-1", context());
        List<Interaction> interactions = List.of(interaction("iid-1", InteractionType.DNS));
        when(client.getAllInteractions()).thenReturn(interactions);

        poller.start();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(1));
        AuditIssue issue = reported.get(0);
        assertThat(issue.name()).isEqualTo("MCP Tool Argument Code Execution");
        assertThat(issue.detail()).contains("format_quote::format");
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
    }

    @Test
    void httpInteractionYieldsCertainConfidence() {
        poller = pollerWithClient();
        poller.sharedClient();
        poller.register("iid-1", context());
        List<Interaction> interactions = List.of(interaction("iid-1", InteractionType.HTTP));
        when(client.getAllInteractions()).thenReturn(interactions);

        poller.start();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(1));
        assertThat(reported.get(0).confidence()).isEqualTo(AuditIssueConfidence.CERTAIN);
    }

    @Test
    void unmatchedInteractionReportsNothing() {
        poller = pollerWithClient();
        poller.sharedClient();
        poller.register("iid-1", context());
        List<Interaction> interactions = List.of(interaction("other-id", InteractionType.DNS));
        when(client.getAllInteractions()).thenReturn(interactions);

        poller.start();

        org.awaitility.Awaitility.await().during(java.time.Duration.ofMillis(150))
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).isEmpty());
    }

    @Test
    void interactionReportedOnlyOnceAcrossPolls() {
        poller = pollerWithClient();
        poller.sharedClient();
        poller.register("iid-1", context());
        List<Interaction> interactions = List.of(interaction("iid-1", InteractionType.DNS));
        when(client.getAllInteractions()).thenReturn(interactions);

        poller.start();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(1));
        org.awaitility.Awaitility.await().during(java.time.Duration.ofMillis(150))
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(1));
    }

    @Test
    void interactionMatchingStillRegisteredContextIsReportedAfterDisconnect() {
        // The poller is extension-scoped: there is no disconnect-time clear. A late callback
        // matching a context registered before disconnect is still correlated and reported.
        poller = pollerWithClient();
        poller.sharedClient();
        poller.register("iid-1", context());
        List<Interaction> interactions = List.of(interaction("iid-1", InteractionType.DNS));
        when(client.getAllInteractions()).thenReturn(interactions);

        poller.start();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(1));
    }

    @Test
    void interactionSeenBeforeContextRegisteredIsNotPermanentlySuppressed() {
        // Scanner-thread vs poll-thread race: a poll observes the interaction before register()
        // commits. The id must NOT be burned, so the next poll (after register) still reports it.
        MutableClock clock = new MutableClock(1_000L);
        poller = clockedPoller(clock);
        poller.sharedClient();
        // Another probe is in flight (map non-empty) so poll() does not short-circuit; the
        // interaction we care about belongs to a payload whose register() has not committed yet.
        poller.register("other-iid", context());
        Interaction interaction = interaction("iid-1", InteractionType.DNS);
        when(client.getAllInteractions()).thenReturn(List.of(interaction));

        poller.poll();
        assertThat(reported).isEmpty();

        poller.register("iid-1", context());
        poller.poll();

        assertThat(reported).hasSize(1);
    }

    @Test
    void contextsOlderThanTtlAreEvictedAndNoLongerReport() {
        MutableClock clock = new MutableClock(0L);
        poller = clockedPoller(clock);
        poller.sharedClient();
        poller.register("iid-1", context());
        Interaction interaction = interaction("iid-1", InteractionType.DNS);
        when(client.getAllInteractions()).thenReturn(List.of(interaction));

        clock.advance(CollaboratorPoller.CONTEXT_TTL.toMillis() + 1);
        poller.poll();

        assertThat(reported).isEmpty();
    }

    @Test
    void sizeCapEvictsOldestContexts() {
        MutableClock clock = new MutableClock(0L);
        poller = clockedPoller(clock);

        for (int i = 0; i <= CollaboratorPoller.MAX_TRACKED_CONTEXTS; i++) {
            clock.advance(1);
            poller.register("iid-" + i, context());
        }

        // The oldest entry (iid-0) was evicted to keep the map at the cap.
        poller.sharedClient();
        Interaction interaction = interaction("iid-0", InteractionType.DNS);
        when(client.getAllInteractions()).thenReturn(List.of(interaction));
        poller.poll();

        assertThat(reported).isEmpty();
    }

    @Test
    void shutdownStopsScheduler() {
        poller = pollerWithClient();
        poller.start();

        poller.shutdown();

        assertThat(poller.isShutdown()).isTrue();
    }

    @Test
    void pollIntervalConstantIsAtLeastSixtySeconds() {
        assertThat(CollaboratorPoller.POLL_INTERVAL)
                .isGreaterThanOrEqualTo(Duration.ofSeconds(60));
    }
}
