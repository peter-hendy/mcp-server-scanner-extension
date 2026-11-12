package com.mcpscanner.checks.catalog.originhostadmission;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import burp.api.montoya.http.HttpService;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OriginHostAdmissionCheckFactoryTest {

    @Test
    void createsTheOriginHostAdmissionCheck() {
        assertThat(new OriginHostAdmissionCheckFactory().create(CheckServices.standalone()))
                .isInstanceOf(OriginHostAdmissionCheck.class);
    }

    @Test
    void runsOnItsOwnSseStreamWhenTheSessionHasAStreamProxy() {
        MontoyaTestFactory.install();
        HttpService streamProxy = HttpService.httpService("127.0.0.1", 40000, false);
        CheckServices services = new CheckServices(McpEventLog.noop(), NoAuthStrategy::new, Optional::empty,
                () -> Optional.of(streamProxy), PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown());
        AdmissionServers.Server server = new AdmissionServers.Server(request -> AdmissionServers.ok(
                AdmissionServers.TOOLS_RESULT));

        ((OriginHostAdmissionCheck) new OriginHostAdmissionCheckFactory().create(services)).audit(
                AdmissionServers.captured(AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL)), server.http);

        assertThat(server.received).isNotEmpty()
                .allSatisfy(request -> assertThat(request.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)).isTrue());
    }

    @Test
    void isDiscoveredThroughTheServiceFile() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("origin-host-admission"))
                .containsInstanceOf(OriginHostAdmissionCheck.class);
    }
}
