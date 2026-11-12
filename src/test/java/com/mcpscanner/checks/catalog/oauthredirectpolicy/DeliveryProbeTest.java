package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.catalog.oauthredirectpolicy.FakeAuthorizationServer.AuthorizationReply;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryProbeTest {

    private static final URI REGISTERED = URI.create("https://client.example.com/cb");
    private static final URI CANDIDATE = URI.create("https://client.example.com/other");
    private static final DeliveryCase STANDARD_CASE = DeliveryCase.of("other path", CANDIDATE,
            AuthorizationVariant.STANDARD);

    private FakeAuthorizationServer server;
    private OwnedClient client;
    private DeliveryProbe probe;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        server = new FakeAuthorizationServer();
        client = server.ownedClient(REGISTERED);
        probe = server.deliveryProbe();
    }

    @Test
    void exposesTheAuthorizationEndpoint() {
        assertThat(probe.authorizationEndpoint()).isEqualTo(FakeAuthorizationServer.AUTHORIZATION_ENDPOINT);
    }

    @Test
    void reportsCodeDeliveredToAnUnregisteredCandidate() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        Optional<RedirectFinding> finding = probe.probe(client, STANDARD_CASE);

        assertThat(finding).hasValueSatisfying(found -> {
            assertThat(found.kind()).isEqualTo(FindingKind.CODE_DELIVERY);
            assertThat(found.location()).isEqualTo(FakeAuthorizationServer.AUTHORIZATION_ENDPOINT);
            assertThat(found.summary()).isEqualTo(STANDARD_CASE.describe(Delivery.CODE));
        });
    }

    @Test
    void reportsErrorDeliveredToAnUnregisteredCandidate() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());
        DeliveryCase unsupported = DeliveryCase.of("other path", CANDIDATE,
                AuthorizationVariant.UNSUPPORTED_RESPONSE_TYPE);

        assertThat(probe.probe(client, unsupported))
                .hasValueSatisfying(found -> assertThat(found.kind()).isEqualTo(FindingKind.ERROR_DELIVERY));
    }

    @Test
    void findingEvidenceIsRegistrationThenControlThenProbe() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        RedirectFinding finding = probe.probe(client, STANDARD_CASE).orElseThrow();

        assertThat(finding.evidence()).hasSize(3);
        assertThat(finding.evidence().get(0)).isSameAs(client.registration().evidence());
        assertThat(QueryParameters.of(finding.evidence().get(1).request().url()))
                .containsEntry("redirect_uri", REGISTERED.toString());
        assertThat(QueryParameters.of(finding.evidence().get(2).request().url()))
                .containsEntry("redirect_uri", CANDIDATE.toString());
    }

    @Test
    void reportsNothingWhenTheServerMatchesExactly() {
        assertThat(probe.probe(client, STANDARD_CASE)).isEmpty();
        assertThat(probe.observe(client, STANDARD_CASE).delivery()).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void controlAndProbeShareOneStateAndTheOwnedClientId() {
        probe.observe(client, STANDARD_CASE);

        List<Map<String, String>> requests = server.authorizations().stream()
                .map(HttpRequest::url).map(QueryParameters::of).toList();
        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(parameters -> parameters.get("client_id"))
                .containsOnly(client.clientId());
        assertThat(requests.get(0).get("state")).isNotBlank().isEqualTo(requests.get(1).get("state"));
    }

    @Test
    void eachObservationUsesAFreshState() {
        probe.observe(client, STANDARD_CASE);
        probe.observe(client, STANDARD_CASE);

        List<String> states = server.authorizations().stream()
                .map(request -> QueryParameters.of(request.url()).get("state")).toList();
        assertThat(states.get(0)).isNotEqualTo(states.get(2));
    }

    @Test
    void unconfirmedCandidateSendsNoAuthorizationRequest() {
        DeliveryCase registeredCandidate = DeliveryCase.of("registered", REGISTERED, AuthorizationVariant.STANDARD);

        DeliveryObservation observation = probe.observe(client, registeredCandidate);

        assertThat(observation.delivery()).isEqualTo(Delivery.INCONCLUSIVE);
        assertThat(observation.exchanges()).isEmpty();
        assertThat(server.authorizations()).isEmpty();
    }

    @Test
    void normalisingCaseReportsCodeDeliveredToTheNormalisedPath() {
        server.redirecting((redirectUri, registered) -> AuthorizationReply.DELIVER_NORMALISED);
        DeliveryCase traversal = DeliveryCase.normalising("dot segment", URI.create(REGISTERED + "/../bypass"));

        assertThat(probe.probe(client, traversal))
                .hasValueSatisfying(found -> assertThat(found.kind()).isEqualTo(FindingKind.CODE_DELIVERY));
    }

    @Test
    void nonNormalisingCaseIgnoresDeliveryToTheNormalisedPath() {
        server.redirecting((redirectUri, registered) -> AuthorizationReply.DELIVER_NORMALISED);
        DeliveryCase traversal = DeliveryCase.of("dot segment", URI.create(REGISTERED + "/../bypass"),
                AuthorizationVariant.STANDARD);

        assertThat(probe.observe(client, traversal).delivery()).isEqualTo(Delivery.INCONCLUSIVE);
    }
}
