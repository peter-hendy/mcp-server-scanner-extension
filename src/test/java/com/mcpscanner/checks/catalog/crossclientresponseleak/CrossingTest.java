package com.mcpscanner.checks.catalog.crossclientresponseleak;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CrossingTest {

    @Test
    void aReplyWithAnotherClientsMarkerInsteadOfItsOwnCrosses() {
        ClientReply a = reply("marker-a", "echo:marker-b");
        ClientReply b = reply("marker-b", "echo:marker-a");

        assertThat(Crossing.in(List.of(a, b))).hasValueSatisfying(crossing -> {
            assertThat(crossing.recipient()).isSameAs(a);
            assertThat(crossing.owner()).isSameAs(b);
        });
    }

    @Test
    void anOwnerThatGotItsOwnReplyWasNotRobbed() {
        ClientReply a = reply("marker-a", "echo:marker-b");
        ClientReply b = reply("marker-b", "echo:marker-b");
        ClientReply c = reply("marker-c", "echo:marker-c");

        assertThat(Crossing.in(List.of(a, b, c))).isEmpty();
    }

    @Test
    void anUnansweredOwnerWasRobbed() {
        ClientReply a = reply("marker-a", "echo:marker-b");
        ClientReply b = ClientReply.unanswered("marker-b");

        assertThat(Crossing.in(List.of(a, b))).hasValueSatisfying(crossing ->
                assertThat(crossing.owner()).isSameAs(b));
    }

    @Test
    void aReplyThatAlsoCarriesItsOwnMarkerDoesNotCross() {
        ClientReply a = reply("marker-a", "notes:marker-a,marker-b");
        ClientReply b = reply("marker-b", "notes:marker-a,marker-b");

        assertThat(Crossing.in(List.of(a, b))).isEmpty();
    }

    @Test
    void aRecipientThatGotItsOwnReplyIsNotARecipientOfAnotherClientsReply() {
        ClientReply a = reply("marker-a", "echo:marker-a and marker-b");
        ClientReply b = ClientReply.unanswered("marker-b");

        assertThat(Crossing.in(List.of(a, b))).isEmpty();
    }

    @Test
    void ownRepliesDoNotCross() {
        assertThat(Crossing.in(List.of(reply("marker-a", "echo:marker-a"), reply("marker-b", "")))).isEmpty();
    }

    private static ClientReply reply(String marker, String body) {
        return new ClientReply(marker, 200, body, Optional.empty());
    }
}
