package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.request.ClientRegistrationMessage;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;
import com.mcpscanner.testutil.LoopbackCollaborator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class ClientRegistrationScanIT {

    private static final String FIELD_PREFIX = "<p>Field: ";

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void outboundCheckProbesTheFetchedUrlFieldsOfTheGeneratedRegistration() throws Exception {
        assertThat(scan("vulnerable")).extracting(ClientRegistrationScanIT::location).containsExactlyInAnyOrder(
                "OAuth parameter client_uri in POST /register",
                "OAuth parameter logo_uri in POST /register");
    }

    @Test
    void outboundCheckStaysSilentWhenRegistrationFetchesNothing() throws Exception {
        assertThat(scan("safe")).isEmpty();
    }

    private static List<AuditIssue> scan(String mode) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--default-mode", "safe", "--vuln", "fetch_oauth_client_metadata=" + mode);
             LoopbackCollaborator collaborator = LoopbackCollaborator.start()) {
            McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                    CheckServices.standalone().authStrategy(), () -> Optional.of(collaborator), Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                    .find("outbound-request").orElseThrow();
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            HttpRequestResponse baseline = http.sendRequest(new ClientRegistrationRequestFactory()
                    .build(generatedRegistration(URI.create("http://127.0.0.1:" + server.port() + "/register"))));

            assertThat((int) baseline.response().statusCode()).isBetween(200, 299);
            return new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                    .run(check, List.of(baseline));
        }
    }

    private static ClientRegistrationMessage generatedRegistration(URI registrationEndpoint) {
        ServerSurface surface = new ServerSurface(new ScanInventory(List.of(), List.of(), List.of(), List.of()),
                ServerCapabilities.none(), null, () -> Optional.of(registrationEndpoint));
        return (ClientRegistrationMessage) RequestGenerator.standard()
                .scanAll(surface, family -> family == RequestFamily.CLIENT_REGISTRATION).messages().get(0);
    }

    private static String location(AuditIssue issue) {
        String detail = issue.detail();
        return detail.substring(FIELD_PREFIX.length(), detail.indexOf("</p>"));
    }
}
