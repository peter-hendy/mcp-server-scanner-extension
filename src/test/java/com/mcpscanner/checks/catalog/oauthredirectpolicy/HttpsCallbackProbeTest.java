package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.catalog.oauthredirectpolicy.FakeAuthorizationServer.AuthorizationReply;
import com.mcpscanner.checks.catalog.oauthredirectpolicy.FakeAuthorizationServer.RegistrationReply;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpsCallbackProbeTest {

    private FakeAuthorizationServer server;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        server = new FakeAuthorizationServer();
    }

    private List<RedirectFinding> run() {
        return new HttpsCallbackProbe().run(server.probeContext());
    }

    private static AuthorizationReply deliverWhen(boolean condition) {
        return condition ? AuthorizationReply.DELIVER : AuthorizationReply.REJECT;
    }

    @Test
    void reportsNothingWhenNoHttpsClientCanBeRegistered() {
        server.registering(uris -> RegistrationReply.FAIL);

        assertThat(run()).isEmpty();
        assertThat(server.authorizations()).isEmpty();
    }

    @Test
    void reportsNothingWhenTheServerMatchesExactly() {
        assertThat(run()).isEmpty();
        assertThat(server.authorizations()).hasSize(4);
    }

    @Test
    void reportsHttpDowngradeOfTheRegisteredUri() {
        server.redirecting((redirectUri, registered) -> deliverWhen(registered.contains(redirectUri)
                || registered.contains(redirectUri.replaceFirst("^http:", "https:"))));

        List<RedirectFinding> findings = run();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.CODE_DELIVERY);
            assertThat(finding.summary()).contains("http downgrade")
                    .contains("http://client.example.com/scancheck-control-");
        });
    }

    @Test
    void reportsPrefixMatchingThroughADotSegmentPath() {
        server.redirecting((redirectUri, registered) -> deliverWhen(
                registered.stream().anyMatch(redirectUri::startsWith)));

        List<RedirectFinding> findings = run();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.CODE_DELIVERY);
            assertThat(finding.summary()).contains("dot-segment path").contains("/../scancheck-bypass-");
        });
    }

    @Test
    void reportsDeliveryToTheNormalisedDotSegmentPath() {
        server.redirecting((redirectUri, registered) -> registered.contains(redirectUri)
                ? AuthorizationReply.DELIVER
                : redirectUri.contains("/../") ? AuthorizationReply.DELIVER_NORMALISED : AuthorizationReply.REJECT);

        assertThat(run()).singleElement()
                .satisfies(finding -> assertThat(finding.summary()).contains("dot-segment path"));
    }

    @Test
    void reportsBothShapesWhenAnyRedirectIsAccepted() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        assertThat(run()).hasSize(2);
    }
}
