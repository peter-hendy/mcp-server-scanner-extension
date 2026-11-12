package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegistrationTest {

    private final HttpRequestResponse exchange = mock(HttpRequestResponse.class);
    private final RegisteredClient client = new RegisteredClient("c1",
            List.of("https://client.example.com/a", "https://client.example.com/b"), exchange, Optional.empty());

    @Test
    void acceptedCarriesTheClientAndItsEvidence() {
        Registration accepted = Registration.accepted(client);

        assertThat(accepted.status()).isEqualTo(RegistrationStatus.ACCEPTED);
        assertThat(accepted.client()).contains(client);
        assertThat(accepted.exchange()).contains(exchange);
    }

    @Test
    void rejectedCarriesTheExchangeButNoClient() {
        Registration rejected = Registration.rejected(exchange);

        assertThat(rejected.status()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(rejected.client()).isEmpty();
        assertThat(rejected.exchange()).contains(exchange);
    }

    @Test
    void inconclusiveKeepsWhateverExchangeExists() {
        assertThat(Registration.inconclusive(Optional.of(exchange)).exchange()).contains(exchange);
        assertThat(Registration.inconclusive(Optional.empty()).exchange()).isEmpty();
        assertThat(Registration.inconclusive(Optional.empty()).status()).isEqualTo(RegistrationStatus.INCONCLUSIVE);
    }

    @Test
    void clientListingAllRequestedUrisIsReturned() {
        assertThat(Registration.accepted(client).clientListingAll(
                List.of("https://client.example.com/a", "https://client.example.com/b"))).contains(client);
    }

    @Test
    void clientListingAllMatchesEchoedUrisCaseInsensitively() {
        assertThat(Registration.accepted(client).clientListingAll(List.of("HTTPS://CLIENT.EXAMPLE.COM/A")))
                .contains(client);
    }

    @Test
    void clientMissingAnyRequestedUriIsNotReturned() {
        assertThat(Registration.accepted(client).clientListingAll(
                List.of("https://client.example.com/a", "https://client.example.com/c"))).isEmpty();
    }

    @Test
    void rejectedRegistrationListsNothing() {
        assertThat(Registration.rejected(exchange).clientListingAll(List.of())).isEmpty();
    }

    @Test
    void inconclusiveRegistrationListsNothing() {
        assertThat(Registration.inconclusive(Optional.of(exchange)).clientListingAll(List.of())).isEmpty();
    }
}
