package com.mcpscanner.checks.catalog.outboundrequest;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundRequestCheckFactoryTest {

    @Test
    void isDiscoveredAsAnInsertionPointCheck() {
        McpCheck check = CheckCatalog.discover(CheckServices.standalone()).find("outbound-request").orElseThrow();

        assertThat(check).isInstanceOf(OutboundRequestCheck.class);
        assertThat(check.descriptor().kind()).isEqualTo(CheckKind.INSERTION_POINT);
        assertThat(check.descriptor().defaultEnabled()).isTrue();
        assertThat(check.requiresReply()).isFalse();
    }

    @Test
    void asksForCollaboratorOnlyWhenTheCheckFirstRuns() {
        AtomicInteger requests = new AtomicInteger();
        CheckServices services = new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, () -> {
            requests.incrementAndGet();
            return Optional.empty();
        }, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown());

        new OutboundRequestCheckFactory().create(services);

        assertThat(requests).hasValue(0);
    }
}
