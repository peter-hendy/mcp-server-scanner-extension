package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionType;
import com.mcpscanner.testutil.Polling;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollaboratorInteractionsTest {

    private final AtomicLong clock = new AtomicLong();
    private final FakeCollaborator collaborator = new FakeCollaborator();

    @Test
    void isUnavailableWhileCollaboratorIsMissing() {
        CollaboratorInteractions interactions = interactionsOver(Optional::empty);

        assertThat(interactions.available()).isFalse();
        assertThat(interactions.newPayload()).isEmpty();
        assertThat(interactions.pollIfDue(Duration.ZERO)).isFalse();
    }

    @Test
    void becomesAvailableWhenCollaboratorIsEnabledLater() {
        AtomicInteger requests = new AtomicInteger();
        CollaboratorInteractions interactions = interactionsOver(() ->
                requests.incrementAndGet() == 1 ? Optional.empty() : Optional.of(collaborator));

        assertThat(interactions.available()).isFalse();
        assertThat(interactions.available()).isTrue();
        assertThat(interactions.newPayload()).isPresent();
        assertThat(requests).hasValue(2);
    }

    @Test
    void isUnavailableWhileTheCollaboratorSupplierFails() {
        CollaboratorInteractions interactions = interactionsOver(() -> {
            throw new IllegalStateException("no Burp Collaborator");
        });

        assertThat(interactions.available()).isFalse();
        assertThat(interactions.newPayload()).isEmpty();
    }

    @Test
    void mintsNoPayloadWhenPayloadGenerationFails() {
        CollaboratorClient failing = mock(CollaboratorClient.class);
        when(failing.generatePayload()).thenThrow(new IllegalStateException("Collaborator disabled"));

        assertThat(interactionsOver(() -> Optional.of(failing)).newPayload()).isEmpty();
    }

    @Test
    void ignoresInteractionsWithoutAPayloadId() {
        CollaboratorClient anonymous = mock(CollaboratorClient.class);
        CollaboratorPayload payload = FakeCollaborator.payload("a");
        Interaction withoutId = mock(Interaction.class);
        when(anonymous.generatePayload()).thenReturn(payload);
        when(anonymous.getAllInteractions()).thenReturn(List.of(withoutId));
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(anonymous));

        assertThat(polledFor(interactions, List.of(interactions.newPayload().orElseThrow())).of(payload)).isEmpty();
    }

    @Test
    void reportsReceivedInteractionsWithoutForgettingThemUntilAsked() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        CollaboratorPayload payload = interactions.newPayload().orElseThrow();
        collaborator.interact(payload, InteractionType.DNS);
        interactions.pollIfDue(Duration.ZERO);

        assertThat(interactions.received(List.of(payload)).of(payload)).hasSize(1);
        assertThat(interactions.received(List.of(payload)).of(payload)).hasSize(1);
        interactions.forget(List.of(payload));
        assertThat(interactions.received(List.of(payload)).isEmpty()).isTrue();
    }

    @Test
    void pollsNowEvenWithinThePollInterval() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        interactions.newPayload().orElseThrow();

        interactions.pollIfDue(Duration.ZERO);
        interactions.pollIfDue(Duration.ZERO);

        assertThat(collaborator.pollCount()).isEqualTo(2);
    }

    @Test
    void pollsAtMostOncePerMinimumInterval() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        interactions.newPayload().orElseThrow();

        boolean first = interactions.pollIfDue(Duration.ofSeconds(1));
        boolean tooSoon = interactions.pollIfDue(Duration.ofSeconds(1));
        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        boolean due = interactions.pollIfDue(Duration.ofSeconds(1));

        assertThat(List.of(first, tooSoon, due)).containsExactly(true, false, true);
        assertThat(collaborator.pollCount()).isEqualTo(2);
    }


    @Test
    void createsOneClientAndMintsADistinctPayloadEachTime() {
        AtomicInteger requests = new AtomicInteger();
        CollaboratorInteractions interactions = interactionsOver(() -> {
            requests.incrementAndGet();
            return Optional.of(collaborator);
        });

        CollaboratorPayload first = interactions.newPayload().orElseThrow();
        CollaboratorPayload second = interactions.newPayload().orElseThrow();

        assertThat(first.toString()).isNotEqualTo(second.toString());
        assertThat(requests).hasValue(1);
    }

    @Test
    void returnsOnlyTheInteractionsOfTheAwaitedPayloads() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        CollaboratorPayload awaited = interactions.newPayload().orElseThrow();
        CollaboratorPayload other = interactions.newPayload().orElseThrow();
        collaborator.interact(awaited, InteractionType.DNS);
        collaborator.interact(other, InteractionType.HTTP);

        ObservedInteractions observed = polledFor(interactions, List.of(awaited));

        assertThat(observed.of(awaited)).extracting(interaction -> interaction.type())
                .containsExactly(InteractionType.DNS);
        assertThat(observed.of(other)).isEmpty();
    }

    @Test
    void keepsAnotherWaitersInteractionsUntilItAwaitsThem() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        CollaboratorPayload first = interactions.newPayload().orElseThrow();
        CollaboratorPayload second = interactions.newPayload().orElseThrow();
        collaborator.interact(second, InteractionType.HTTP);

        polledFor(interactions, List.of(first));

        assertThat(polledFor(interactions, List.of(second)).of(second)).hasSize(1);
    }

    @Test
    void countsAnInteractionOnceWhenTheClientReturnsItOnEveryPoll() {
        CollaboratorClient replaying = mock(CollaboratorClient.class);
        CollaboratorPayload payload = FakeCollaborator.payload("r");
        when(replaying.generatePayload()).thenReturn(payload);
        when(replaying.getAllInteractions()).thenAnswer(invocation -> List.of(
                FakeCollaborator.interaction("r", InteractionType.DNS, null)));
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(replaying));

        assertThat(polledFor(interactions, List.of(interactions.newPayload().orElseThrow())).of(payload)).hasSize(1);
    }

    @Test
    void dropsInteractionsForPayloadsNobodyIsAwaiting() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        CollaboratorPayload awaited = interactions.newPayload().orElseThrow();
        collaborator.interactWithUnknownPayload("stranger");

        assertThat(polledFor(interactions, List.of(awaited)).of(FakeCollaborator.payload("stranger"))).isEmpty();
    }




    @Test
    void treatsAFailingPollAsNoInteractions() {
        CollaboratorClient failing = mock(CollaboratorClient.class);
        when(failing.generatePayload()).thenReturn(FakeCollaborator.payload("f"));
        when(failing.getAllInteractions()).thenThrow(new IllegalStateException("collaborator offline"));
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(failing));

        CollaboratorPayload payload = interactions.newPayload().orElseThrow();

        assertThat(polledFor(interactions, List.of(payload)).of(payload)).isEmpty();
    }



    @Test
    void releasesEveryPayloadOfAProbeThatFailsBeforeItIsAwaited() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));
        List<CollaboratorPayload> minted = new ArrayList<>();

        assertThatThrownBy(() -> interactions.probing(payloads -> {
            payloads.newPayload().ifPresent(minted::add);
            payloads.newPayload().ifPresent(minted::add);
            throw new IllegalStateException("probe send failed");
        })).hasMessage("probe send failed");
        minted.forEach(payload -> collaborator.interact(payload, InteractionType.DNS));
        interactions.pollIfDue(Duration.ZERO);

        assertThat(minted).hasSize(2);
        assertThat(interactions.received(minted).isEmpty()).isTrue();
    }

    @Test
    void keepsAwaitingThePayloadsOfAProbeThatSucceeds() {
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(collaborator));

        CollaboratorPayload payload = interactions.probing(payloads -> payloads.newPayload().orElseThrow());
        collaborator.interact(payload, InteractionType.DNS);
        interactions.pollIfDue(Duration.ZERO);

        assertThat(interactions.received(List.of(payload)).of(payload)).hasSize(1);
    }

    @Test
    void readsAndMintsWhileAnotherThreadsPollIsInFlight() throws Exception {
        StalledCollaborator stalled = new StalledCollaborator();
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(stalled));
        CollaboratorPayload payload = interactions.newPayload().orElseThrow();
        CompletableFuture<Void> polling = CompletableFuture.runAsync(() -> interactions.pollIfDue(Duration.ZERO));
        try {
            stalled.awaitPollStarted();

            assertThat(promptly(() -> interactions.received(List.of(payload)).isEmpty())).isTrue();
            assertThat(promptly(interactions::newPayload)).isPresent();
        } finally {
            stalled.releasePoll();
            doneWithin(polling, Duration.ofSeconds(5)).join();
        }
    }

    @Test
    void concurrentPollsShareTheInFlightPoll() throws Exception {
        StalledCollaborator stalled = new StalledCollaborator();
        CollaboratorInteractions interactions = interactionsOver(() -> Optional.of(stalled));
        CollaboratorPayload payload = interactions.newPayload().orElseThrow();
        stalled.interact(payload, InteractionType.DNS);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> interactions.pollIfDue(Duration.ZERO));
        stalled.awaitPollStarted();
        CompletableFuture<Integer> second = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            interactions.pollIfDue(Duration.ZERO);
            second.complete(interactions.received(List.of(payload)).of(payload).size());
        });
        waiter.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> waiter.getState() == Thread.State.WAITING);

        stalled.releasePoll();

        assertThat(doneWithin(second, Duration.ofSeconds(5)).join()).isEqualTo(1);
        doneWithin(first, Duration.ofSeconds(5)).join();
        assertThat(stalled.pollCount()).isEqualTo(1);
    }

    private static <T> T promptly(Supplier<T> action) {
        return doneWithin(CompletableFuture.supplyAsync(action), Duration.ofSeconds(1)).join();
    }

    private CollaboratorInteractions interactionsOver(Supplier<Optional<CollaboratorClient>> clients) {
        return new CollaboratorInteractions(clients, clock::get);
    }

    private static ObservedInteractions polledFor(CollaboratorInteractions interactions,
                                                  List<CollaboratorPayload> payloads) {
        interactions.pollIfDue(Duration.ZERO);
        return interactions.received(payloads);
    }
}
