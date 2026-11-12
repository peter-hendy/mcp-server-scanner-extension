package com.mcpscanner.checks.api;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckServicesTest {

    @Test
    void standaloneServicesNeedNoBurpOrLiveSession() {
        CheckServices services = CheckServices.standalone();

        assertThat(services.authStrategy().get()).isInstanceOf(NoAuthStrategy.class);
        assertThat(services.collaboratorWatch().available()).isFalse();
        assertThat(services.lateIssues()).isSameAs(LateIssueSink.DISCARD);
        assertThat(services.burpNativeFields()).isEqualTo(com.mcpscanner.mcp.BurpNativeJsonRpcFields.ABSENT);
        assertThat(services.probeService().get()).isEmpty();
        assertThat(services.eventLog()).isNotNull();
        assertThat(services.principals().get().compared()).isEmpty();
        assertThat(services.accessLedger().expectationFor(new AccessItemKey(AccessItemKey.Kind.TOOL, "t")))
                .isEqualTo(AccessExpectation.ANY_SIGNED_IN);
        assertThat(services.principalListings().lists("bob", new AccessItemKey(AccessItemKey.Kind.TOOL, "t")))
                .isEmpty();
    }

    @Test
    void reportingLateIssuesKeepsTheBurpNativeFieldSignal() {
        CheckServices services = new CheckServices(McpEventLog.noop(), NoAuthStrategy::new,
                CheckServices.standalone().collaboratorWatch(), Optional::empty, PrincipalRoster::empty,
                AccessLedger.noop(), PrincipalListings.unknown(), Optional::empty, LateIssueSink.DISCARD,
                com.mcpscanner.mcp.BurpNativeJsonRpcFields.PRESENT);

        assertThat(services.reportingLateIssues(services.collaboratorWatch(), LateIssueSink.DISCARD)
                .burpNativeFields().present()).isTrue();
    }

    @Test
    void rejectsMissingServices() {
        assertThatThrownBy(() -> new CheckServices(null, NoAuthStrategy::new, Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), null, Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, null, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, Optional::empty, null,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, Optional::empty,
                Optional::empty, null, AccessLedger.noop(), PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, Optional::empty,
                Optional::empty, PrincipalRoster::empty, null, PrincipalListings.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, Optional::empty,
                Optional::empty, PrincipalRoster::empty, AccessLedger.noop(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
