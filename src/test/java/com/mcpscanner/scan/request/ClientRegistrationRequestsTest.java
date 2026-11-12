package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRegistrationRequestsTest {

    private final ClientRegistrationRequests requests = new ClientRegistrationRequests();

    @Test
    void producesOneRegistrationCarryingTheFullClientMetadataWhenTheServerAdvertisesAnEndpoint() {
        ServerSurface surface = SampleSurfaces.advertisingRegistration();

        List<? extends ScanMessage> messages = requests.build(surface);

        assertThat(requests.supportedBy(surface)).isTrue();
        assertThat(messages).singleElement().isInstanceOfSatisfying(ClientRegistrationMessage.class, message -> {
            assertThat(message.family()).isEqualTo(RequestFamily.CLIENT_REGISTRATION);
            assertThat(message.registrationEndpoint()).isEqualTo(SampleSurfaces.REGISTRATION_ENDPOINT);
            assertThat(message.metadata().fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "redirect_uris", "token_endpoint_auth_method", "grant_types", "response_types",
                    "client_name", "client_uri", "logo_uri", "scope", "contacts", "tos_uri", "policy_uri",
                    "software_id", "software_version", "initiate_login_uri");
        });
    }

    @Test
    void everyClientMetadataValueIsPopulated() {
        JsonNode metadata = registration().metadata();

        metadata.fields().forEachRemaining(field -> assertThat(field.getValue().isContainerNode()
                ? field.getValue().size() : field.getValue().asText().length()).as(field.getKey()).isPositive());
        assertThat(metadata.path("redirect_uris").get(0).asText()).startsWith("http://127.0.0.1");
        assertThat(metadata.path("grant_types")).extracting(JsonNode::asText)
                .containsExactly("authorization_code", "refresh_token");
        assertThat(metadata.path("response_types")).extracting(JsonNode::asText).containsExactly("code");
        assertThat(metadata.path("token_endpoint_auth_method").asText()).isEqualTo("none");
    }

    @Test
    void publicClientDeclaresNoKeySetAStrictOpenIdProviderWouldRejectAlongsideAuthMethodNone() {
        JsonNode metadata = registration().metadata();

        assertThat(metadata.path("token_endpoint_auth_method").asText()).isEqualTo("none");
        assertThat(metadata.has("jwks_uri")).isFalse();
        assertThat(metadata.has("jwks")).isFalse();
    }

    @Test
    void declaresNoSectorIdentifierAStrictOpenIdProviderWouldHaveToFetchAndMatch() {
        assertThat(registration().metadata().has("sector_identifier_uri")).isFalse();
    }

    @Test
    void keepsEveryHttpsUrlFieldAnAuthorizationServerMayFetch() {
        JsonNode metadata = registration().metadata();

        for (String field : List.of("client_uri", "logo_uri", "policy_uri", "tos_uri", "initiate_login_uri")) {
            assertThat(metadata.path(field).asText()).as(field).startsWith("https://");
        }
    }

    @Test
    void producesNothingWhenTheServerAdvertisesNoRegistrationEndpoint() {
        ServerSurface surface = SampleSurfaces.withoutRegistration();

        assertThat(requests.supportedBy(surface)).isFalse();
        assertThat(requests.build(surface)).isEmpty();
    }

    @Test
    void subjectNamesTheRegistrationEndpoint() {
        assertThat(registration().subject()).isEqualTo("OAuth client registration " + SampleSurfaces.REGISTRATION_ENDPOINT);
    }

    @Test
    void registrationIsBoundToTheAdvertisedEndpointNotTheMcpEndpoint() {
        URI elsewhere = URI.create("https://auth.example.test/other/register?tenant=a");
        ServerSurface surface = SampleSurfaces.advertisingRegistration(elsewhere);

        assertThat(((ClientRegistrationMessage) requests.build(surface).get(0)).registrationEndpoint())
                .isEqualTo(elsewhere);
    }

    private ClientRegistrationMessage registration() {
        return (ClientRegistrationMessage) requests.build(SampleSurfaces.advertisingRegistration()).get(0);
    }
}
