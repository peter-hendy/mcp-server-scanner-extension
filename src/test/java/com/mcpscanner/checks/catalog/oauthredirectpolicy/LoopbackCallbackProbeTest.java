package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.catalog.oauthredirectpolicy.FakeAuthorizationServer.RegistrationReply;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackCallbackProbeTest {

    private FakeAuthorizationServer server;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        server = new FakeAuthorizationServer();
    }

    private List<RedirectFinding> run() {
        return new LoopbackCallbackProbe().run(server.probeContext());
    }

    @Test
    void reportsNothingWhenNoLoopbackClientCanBeRegistered() {
        server.registering(uris -> RegistrationReply.REJECT);

        assertThat(run()).isEmpty();
        assertThat(server.authorizations()).isEmpty();
    }

    @Test
    void reportsNothingWhenTheServerMatchesExactly() {
        assertThat(run()).isEmpty();
    }

    @Test
    void probesEveryAuthorizationVariantWithAControlEach() {
        run();

        assertThat(server.authorizations()).hasSize(AuthorizationVariant.values().length * 2);
    }

    @Test
    void reportsCodeForEachCodeVariantAndErrorForTheUnsupportedResponseTypeAndTheDenial() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        List<RedirectFinding> findings = run();

        assertThat(findings).extracting(RedirectFinding::kind).containsExactly(
                FindingKind.CODE_DELIVERY, FindingKind.CODE_DELIVERY, FindingKind.CODE_DELIVERY,
                FindingKind.ERROR_DELIVERY, FindingKind.ERROR_DELIVERY);
        assertThat(findings).extracting(RedirectFinding::summary)
                .allSatisfy(summary -> assertThat(summary)
                        .contains("unregistered path on the registered loopback host and port")
                        .contains("http://127.0.0.1:49151/scancheck-unregistered-"));
    }

    @Test
    void namesTheVariantInEachFinding() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        List<String> summaries = run().stream().map(RedirectFinding::summary).toList();

        Arrays.stream(AuthorizationVariant.values()).forEach(variant ->
                assertThat(summaries).anySatisfy(summary -> assertThat(summary).contains(variant.label())));
    }

    @Test
    void probesOneUnregisteredCandidateAcrossVariants() {
        server.redirecting(FakeAuthorizationServer.anyRedirect());

        List<String> candidates = run().stream()
                .map(finding -> finding.summary().replaceAll(".* to (\\S+) \\(.*", "$1"))
                .distinct()
                .toList();

        assertThat(candidates).hasSize(1);
    }
}
