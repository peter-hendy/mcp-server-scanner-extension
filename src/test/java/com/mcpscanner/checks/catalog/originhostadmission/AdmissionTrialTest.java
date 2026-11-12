package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.session.StreamingFallback;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.FORBIDDEN;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.OTHER_TOOLS_RESULT;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.TOOLS_RESULT;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.ok;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.status;
import static org.assertj.core.api.Assertions.assertThat;

class AdmissionTrialTest {

    private final AdmissionTrial trial = new AdmissionTrial(AdmissionProbe.STANDARD_SET, StreamingFallback.outsideBurp());
    private HttpRequest seed;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        seed = AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL);
    }

    @Test
    void sendsABaselineEveryProbeAndAFinalControl() {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> ok(TOOLS_RESULT));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(server.received).hasSize(7);
        assertThat(server.received.get(0)).isSameAs(seed);
        assertThat(server.received.get(6)).isSameAs(seed);
        assertThat(outcome.conclusive()).isTrue();
        assertThat(outcome.probes()).extracting(ProbeObservation::verdict).containsOnly(ProbeVerdict.ADMITTED);
    }

    @Test
    void aRefusedProbeIsNotAdmitted() {
        AdmissionServers.Server server = new AdmissionServers.Server(
                AdmissionServers.admitting(AdmissionServers.noForeignOrigin()));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(outcome.admittedIn(AdmissionCategory.ORIGIN)).isEmpty();
        assertThat(outcome.admittedIn(AdmissionCategory.HOST_AND_ORIGIN)).isEmpty();
        assertThat(outcome.admittedIn(AdmissionCategory.HOST)).hasSize(1);
        assertThat(outcome.probes()).filteredOn(probe -> probe.probe().category() == AdmissionCategory.ORIGIN)
                .extracting(ProbeObservation::verdict).containsOnly(ProbeVerdict.REFUSED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void aServerErrorOnAProbeIsInconclusiveNotAdmitted(int serverError) {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed
                ? ok(TOOLS_RESULT) : status(serverError, "upstream failed"));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(outcome.conclusive()).isTrue();
        assertThat(outcome.probes()).extracting(ProbeObservation::verdict).containsOnly(ProbeVerdict.INCONCLUSIVE);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 401, 403})
    void stopsAfterTheBaselineWhenItIsNotSuccessful(int baselineStatus) {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> status(baselineStatus, FORBIDDEN));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(server.received).hasSize(1);
        assertThat(outcome.status()).isEqualTo(TrialOutcome.TrialStatus.BASELINE_NOT_SUCCESSFUL);
        assertThat(outcome.probes()).isEmpty();
    }

    @Test
    void aBaselineWithAJsonRpcErrorIsNotSuccessful() {
        AdmissionServers.Server server = new AdmissionServers.Server(request ->
                ok("{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"code\":-32601,\"message\":\"nope\"}}"));

        assertThat(trial.run(seed, server.http).status())
                .isEqualTo(TrialOutcome.TrialStatus.BASELINE_NOT_SUCCESSFUL);
    }

    @Test
    void aSuccessfulProbeWithAnotherResultIsNotAdmitted() {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed
                ? ok(TOOLS_RESULT) : ok(OTHER_TOOLS_RESULT));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(outcome.probes()).extracting(ProbeObservation::verdict)
                .containsOnly(ProbeVerdict.ADMITTED_WITH_ANOTHER_RESULT);
        assertThat(outcome.admittedIn(AdmissionCategory.ORIGIN)).isEmpty();
    }

    @Test
    void aControlWithAnotherResultMakesTheTrialInconclusive() {
        AdmissionServers.Server server = AdmissionServers.Server.sequenced(List.of(
                ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT),
                ok(TOOLS_RESULT), ok(OTHER_TOOLS_RESULT)));

        assertThat(trial.run(seed, server.http).status()).isEqualTo(TrialOutcome.TrialStatus.CONTROL_DIVERGED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429})
    void aFailingControlMakesTheTrialInconclusive(int controlStatus) {
        AdmissionServers.Server server = AdmissionServers.Server.sequenced(List.of(
                ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT), ok(TOOLS_RESULT),
                ok(TOOLS_RESULT), status(controlStatus, "")));

        assertThat(trial.run(seed, server.http).status()).isEqualTo(TrialOutcome.TrialStatus.CONTROL_DIVERGED);
    }

    @Test
    void anUnansweredProbeIsInconclusive() {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed ? ok(TOOLS_RESULT) : null);

        assertThat(trial.run(seed, server.http).probes()).extracting(ProbeObservation::verdict)
                .containsOnly(ProbeVerdict.INCONCLUSIVE);
    }

    @Test
    void anSseFramedReplyIsComparedByItsResult() {
        String sse = "event: message\ndata: " + TOOLS_RESULT + "\n\n";
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed
                ? ok(TOOLS_RESULT) : AdmissionServers.sse(sse));

        assertThat(trial.run(seed, server.http).probes()).extracting(ProbeObservation::verdict)
                .containsOnly(ProbeVerdict.ADMITTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 421})
    void aClientRejectionOfAProbeIsARefusal(int rejection) {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed
                ? ok(TOOLS_RESULT) : status(rejection, "Invalid Host header"));

        TrialOutcome outcome = trial.run(seed, server.http);

        assertThat(outcome.probes()).extracting(ProbeObservation::verdict).containsOnly(ProbeVerdict.REFUSED);
        assertThat(outcome.everyProbeAnswered()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void aTransientProbeFailureLeavesTheTrialNotFullyAnswered(int transientStatus) {
        AdmissionServers.Server server = new AdmissionServers.Server(request -> request == seed
                ? ok(TOOLS_RESULT) : status(transientStatus, ""));

        assertThat(trial.run(seed, server.http).everyProbeAnswered()).isFalse();
    }
}
