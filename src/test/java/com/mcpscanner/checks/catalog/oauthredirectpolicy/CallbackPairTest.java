package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackPairTest {

    private static final URI REGISTERED = URI.create("https://client.example.com/cb");
    private static final URI CANDIDATE = URI.create("https://client.example.com/other");
    private static final List<String> REGISTERED_ONLY = List.of(REGISTERED.toString());

    @Test
    void isConfirmedWhenOnlyTheRegisteredUriIsListed() {
        assertThat(new CallbackPair(REGISTERED, CANDIDATE).confirmedBy(REGISTERED_ONLY)).isTrue();
    }

    @Test
    void isNotConfirmedWhenTheRegisteredUriIsNotListed() {
        assertThat(new CallbackPair(REGISTERED, CANDIDATE).confirmedBy(List.of("https://client.example.com/x")))
                .isFalse();
    }

    @Test
    void isNotConfirmedWhenTheCandidateIsListed() {
        assertThat(new CallbackPair(REGISTERED, CANDIDATE)
                .confirmedBy(List.of(REGISTERED.toString(), CANDIDATE.toString()))).isFalse();
    }

    @Test
    void isNotConfirmedWhenTheCandidateEqualsTheRegisteredUriUpToCase() {
        URI sameUri = URI.create("HTTPS://CLIENT.EXAMPLE.COM/cb");

        assertThat(new CallbackPair(REGISTERED, sameUri).confirmedBy(REGISTERED_ONLY)).isFalse();
    }

    @Test
    void isNotConfirmedWhenTheCandidateCarriesAQuery() {
        URI withQuery = URI.create("https://client.example.com/other?x=1");

        assertThat(new CallbackPair(REGISTERED, withQuery).confirmedBy(REGISTERED_ONLY)).isFalse();
    }

    @Test
    void isNotConfirmedWhenTheCandidateCarriesAFragment() {
        URI withFragment = URI.create("https://client.example.com/other#x");

        assertThat(new CallbackPair(REGISTERED, withFragment).confirmedBy(REGISTERED_ONLY)).isFalse();
    }

    @Test
    void withCandidateKeepsTheRegisteredUri() {
        CallbackPair replaced = new CallbackPair(REGISTERED, CANDIDATE).withCandidate(URI.create("https://x.test/"));

        assertThat(replaced).isEqualTo(new CallbackPair(REGISTERED, URI.create("https://x.test/")));
    }
}
