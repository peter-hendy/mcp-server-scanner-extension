package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.HostAudits;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.ScriptedTrial;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HostCheckAdapterTrialsTest {

    private final Http http = mock(Http.class);
    private final HttpRequestResponse onHostA = AdapterFixtures.baseOn("a.test");
    private final ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
    private final HostCheckAdapter adapter = new HostCheckAdapter(
            AdapterFixtures.settingsAllowing(true), McpEventLog.noop(), new ScriptedTrial.Check(trial));

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void retriesAnInconclusiveTrialOnLaterRequestsUpToTheAttemptLimit() {
        auditRepeatedly(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 2);

        assertThat(trial.runs).hasSize(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);
    }

    @Test
    void clearingConnectionStateResetsTheAttemptLimit() {
        auditRepeatedly(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);

        adapter.clearConnectionState();
        auditRepeatedly(1);

        assertThat(trial.runs).hasSize(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 1);
    }

    private void auditRepeatedly(int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            adapter.doCheck(onHostA, null, http);
        }
    }
}
