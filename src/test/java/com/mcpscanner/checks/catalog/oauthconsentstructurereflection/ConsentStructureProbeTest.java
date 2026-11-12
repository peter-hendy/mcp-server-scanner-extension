package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.ScriptedHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.ANGLES_ESCAPED;
import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.AUTHORIZE;
import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.CLIENT_ID;
import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.MCP;
import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.RAW;
import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.REGISTER;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.queryParameter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConsentStructureProbeTest {

    private static final String PREFIX = "Scan check: test — ";
    private final McpEventLog eventLog = mock(McpEventLog.class);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void rawClientNameAndStateAreBothReportedAsElements() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).state(RAW).build();

        Optional<ConsentStructureEvidence> evidence = probe(server);

        assertThat(evidence.orElseThrow().reflections())
                .extracting(FieldReflection::field, FieldReflection::placement)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ReflectedField.CLIENT_NAME, StructurePlacement.ELEMENT),
                        org.assertj.core.groups.Tuple.tuple(ReflectedField.STATE, StructurePlacement.ELEMENT));
        assertThat(evidence.get().scriptExecution().verdict()).isEqualTo(ScriptExecution.NO_POLICY);
    }

    @Test
    void controlUsesTheSameStateWithAnUnregisteredClient() {
        ScriptedHttp server = new ScriptedConsentServer().state(RAW).build();

        probe(server);

        assertThat(server.sentTo(AUTHORIZE)).hasSize(2);
        assertThat(queryParameter(server.sentTo(AUTHORIZE).get(1), "client_id"))
                .isEqualTo(CLIENT_ID + ConsentStructureProbe.CONTROL_CLIENT_SUFFIX);
        assertThat(queryParameter(server.sentTo(AUTHORIZE).get(1), "state"))
                .isEqualTo(queryParameter(server.sentTo(AUTHORIZE).get(0), "state"));
    }

    @Test
    void registeredClientNameCarriesTheMarkerPayload() {
        ScriptedHttp server = new ScriptedConsentServer().build();

        probe(server);

        assertThat(server.sentTo(REGISTER).get(0).bodyToString()).contains("MCP Scanner mcpscan-")
                .contains("</script><mcpscan-el-").doesNotContain("onerror");
    }

    @Test
    void attributeBreakoutWithEncodedAnglesIsReportedAsAnAttribute() {
        ScriptedHttp server = new ScriptedConsentServer().state(ANGLES_ESCAPED).build();

        assertThat(probe(server).orElseThrow().reflections())
                .singleElement()
                .satisfies(reflection -> {
                    assertThat(reflection.field()).isEqualTo(ReflectedField.STATE);
                    assertThat(reflection.placement()).isEqualTo(StructurePlacement.ATTRIBUTE);
                });
    }

    @Test
    void escapingServerIsSilentAndSendsNoControl() {
        ScriptedHttp server = new ScriptedConsentServer().build();

        assertThat(probe(server)).isEmpty();
        assertThat(server.sentTo(AUTHORIZE)).hasSize(1);
    }

    @Test
    void structureEchoedByTheControlIsDropped() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).state(RAW).controlEchoesStateRaw().build();

        assertThat(probe(server).orElseThrow().reflections())
                .extracting(FieldReflection::field).containsExactly(ReflectedField.CLIENT_NAME);
    }

    @Test
    void onlyControlEchoedStructureIsSilent() {
        ScriptedHttp server = new ScriptedConsentServer().state(RAW).controlEchoesStateRaw().build();

        assertThat(probe(server)).isEmpty();
    }

    @Test
    void nonHtmlControlEchoingTheStateDoesNotSuppressTheFinding() {
        ScriptedHttp server = new ScriptedConsentServer().state(RAW).controlAnswersWithJson().build();

        assertThat(probe(server).orElseThrow().reflections())
                .extracting(FieldReflection::field).containsExactly(ReflectedField.STATE);
    }

    @Test
    void markerOnlyInARedirectBodyIsNotReported() {
        ScriptedHttp server = new ScriptedConsentServer().consentBehindRedirectEchoingState().build();

        assertThat(probe(server)).isEmpty();
        assertThat(server.sentTo(ScriptedConsentServer.ORIGIN + "/consent")).hasSize(1);
    }

    @Test
    void unansweredControlIsInconclusiveAndSilent() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).controlUnanswered().build();

        assertThat(probe(server)).isEmpty();
    }

    @Test
    void noRegistrationEndpointSendsNoRegistration() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).withoutRegistration().build();

        assertThat(probe(server)).isEmpty();
        assertThat(server.sentTo(REGISTER)).isEmpty();
        assertThat(server.sentTo(AUTHORIZE)).isEmpty();
    }

    @Test
    void rejectedRegistrationStopsBeforeAuthorization() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).rejectingRegistration().build();

        assertThat(probe(server)).isEmpty();
        assertThat(server.sentTo(AUTHORIZE)).isEmpty();
    }

    @Test
    void consentPageCspIsAssessedWithoutChangingTheReflections() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW)
                .consentHeaders("Content-Security-Policy: script-src 'self'").build();

        ConsentStructureEvidence evidence = probe(server).orElseThrow();

        assertThat(evidence.reflections()).hasSize(1);
        assertThat(evidence.scriptExecution().verdict()).isEqualTo(ScriptExecution.BLOCKED);
    }

    @Test
    void deletesTheRegisteredClientAfterAFindingWhenTheServerOffersClientManagement() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).managingClients().build();

        assertThat(probe(server)).isPresent();
        assertThat(server.sentTo(ScriptedConsentServer.MANAGED_CLIENT)).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.headerValue("Authorization")).isEqualTo("Bearer rat");
        });
    }

    @Test
    void deletesTheRegisteredClientWhenNothingIsFound() {
        ScriptedHttp server = new ScriptedConsentServer().managingClients().build();

        assertThat(probe(server)).isEmpty();
        assertThat(server.sentTo(ScriptedConsentServer.MANAGED_CLIENT)).hasSize(1);
    }

    @Test
    void stopsAndSaysWhyWhenTheServerAnswersTooManyRequests() {
        ScriptedHttp server = new ScriptedConsentServer().clientName(RAW).managingClients().throttlingAuthorization()
                .build();

        assertThat(probe(server)).isEmpty();

        assertThat(server.sentTo(AUTHORIZE)).hasSize(1);
        assertThat(server.sentTo(ScriptedConsentServer.MANAGED_CLIENT)).isEmpty();
        verify(eventLog).info(PREFIX + "stopped " + MCP + " (rate limited: the server answered HTTP 429)");
    }

    @Test
    void aServerWithoutARegistrationEndpointIsSkippedForAMissingPrerequisite() {
        probe(new ScriptedConsentServer().withoutRegistration().build());

        verify(eventLog).info(PREFIX + "skipped " + MCP
                + " (prerequisite missing: no OAuth authorization server with a registration endpoint was discovered)");
    }

    @Test
    void aRejectedRegistrationIsSkippedForAMissingPrerequisite() {
        probe(new ScriptedConsentServer().rejectingRegistration().build());

        verify(eventLog).info(PREFIX + "skipped " + MCP
                + " (prerequisite missing: dynamic client registration did not issue a client_id)");
    }

    @Test
    void anUnansweredControlStopsTheProbeForATransportFailure() {
        probe(new ScriptedConsentServer().clientName(RAW).controlUnanswered().build());

        verify(eventLog).info(PREFIX + "stopped " + MCP
                + " (transport failure: the unregistered-client control received no response)");
    }

    @Test
    void anAuthorizationPageBurpWithheldAsStreamedIsLoggedAsAStreamedReply() {
        ScriptedHttp server = new ScriptedConsentServer().build().on("GET", AUTHORIZE, request -> {
            throw new IllegalStateException("Streaming response received");
        });

        probe(server);

        verify(eventLog).info(PREFIX + "stopped " + MCP
                + " (streamed reply withheld by Burp: the authorization request received no response)");
    }

    @Test
    void aNegativeResultIsADecisionNotASkip() {
        probe(new ScriptedConsentServer().build());

        verify(eventLog).info(PREFIX + "decision: not reported — no marker parsed as HTML structure on the "
                + "authorization page");
        verify(eventLog, never()).info(contains("skipped"));
    }

    private Optional<ConsentStructureEvidence> probe(ScriptedHttp server) {
        OAuthHttp http = new OAuthHttp(server.http());
        return new ConsentStructureProbe(http, new StructureMarkers(),
                new DecisionLog(new CheckLog(eventLog, "test"), http)).run(ScriptedConsentServer.mcpBase());
    }
}
