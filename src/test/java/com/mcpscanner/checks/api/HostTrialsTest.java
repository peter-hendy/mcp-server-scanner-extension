package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HostTrialsTest {

    private final Http http = mock(Http.class);
    private final AuditIssue first = mock(AuditIssue.class);
    private final AuditIssue second = mock(AuditIssue.class);
    private final HttpRequestResponse base = mock(HttpRequestResponse.class);

    @Test
    void aSettledResultCarriesItsIssuesAndSettlesTheHost() {
        HostTrialResult result = HostTrialResult.settled(List.of(first));

        assertThat(result.issues()).containsExactly(first);
        assertThat(result.settlement()).isEqualTo(HostSettlement.SETTLED);
    }

    @Test
    void aPlainHostCheckIsOneTrialThatAlwaysSettles() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(first), candidate -> candidate == base);

        List<HostTrial> trials = check.trials();

        assertThat(trials).hasSize(1);
        assertThat(trials.get(0).appliesTo(base)).isTrue();
        assertThat(trials.get(0).appliesTo(mock(HttpRequestResponse.class))).isFalse();
        assertThat(trials.get(0).run(base, http)).isEqualTo(HostTrialResult.settled(List.of(first)));
        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(base);
    }

    @Test
    void aPlainHostChecksTrialSaysWhatTheCheckAppliesTo() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(first), candidate -> candidate == base);

        assertThat(check.trials().get(0).whyNotApplicable(mock(HttpRequestResponse.class)))
                .contains(SkipReason.notApplicable("applies only to recorded requests"));
        assertThat(check.trials().get(0).whyNotApplicable(base)).isEmpty();
    }

    @Test
    void aRetryingCheckAppliesWhenAnyTrialApplies() {
        ScriptedTrial never = new ScriptedTrial(HostSettlement.SETTLED, List.of(), candidate -> false);

        assertThat(new ScriptedTrial.Check(never, ScriptedTrial.settling(HostSettlement.SETTLED)).appliesTo(base)).isTrue();
        assertThat(new ScriptedTrial.Check(never).appliesTo(base)).isFalse();
    }

    @Test
    void aRetryingCheckAuditedDirectlyRunsEveryApplicableTrialInOrder() {
        ScriptedTrial one = new ScriptedTrial(HostSettlement.INCONCLUSIVE, List.of(first));
        ScriptedTrial skipped = new ScriptedTrial(HostSettlement.SETTLED, List.of(first), candidate -> false);
        ScriptedTrial two = new ScriptedTrial(HostSettlement.SETTLED, List.of(second));

        List<AuditIssue> issues = new ScriptedTrial.Check(one, skipped, two).audit(base, http);

        assertThat(issues).containsExactly(first, second);
        assertThat(skipped.runs).isEmpty();
    }
}
