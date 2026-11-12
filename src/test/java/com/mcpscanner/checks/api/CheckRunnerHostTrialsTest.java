package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckRunnerHostTrialsTest {

    private final CheckRunner runner = new CheckRunner(mock(Http.class), base -> List.of());

    @Test
    void anInconclusiveTrialIsRetriedOnLaterRequestsToTheSameHostUpToTheAttemptLimit() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        List<HttpRequestResponse> bases = Collections.nCopies(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 2, base("a.test"));

        runner.run(new ScriptedTrial.Check(trial), bases);

        assertThat(trial.runs).hasSize(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);
    }

    @Test
    void eachRunStartsWithNoHostClaimed() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.SETTLED);
        ScriptedTrial.Check check = new ScriptedTrial.Check(trial);

        runner.run(check, List.of(base("a.test")));
        runner.run(check, List.of(base("a.test")));

        assertThat(trial.runs).hasSize(2);
    }

    private static HttpRequestResponse base(String host) {
        HttpService service = HostKeyTest.service(host, 443, true);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.httpService()).thenReturn(service);
        return base;
    }
}
