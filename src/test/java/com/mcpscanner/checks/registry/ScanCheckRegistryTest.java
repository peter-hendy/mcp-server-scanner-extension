package com.mcpscanner.checks.registry;

import burp.StreamingRefusalStandIn;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.http.ConnectionReuse;
import com.mcpscanner.http.UpstreamExchanges;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanCheckRegistryTest {

    private final RecordingChecks.InsertionPoint insertionPointCheck = new RecordingChecks.InsertionPoint("c-ip", List.of());
    private final RecordingChecks.Request requestCheck = new RecordingChecks.Request("b-request", List.of());
    private final RecordingChecks.Host hostCheck = new RecordingChecks.Host("a-host", List.of());
    private ScanCheckSettings settings;
    private ScanCheckRegistry registry;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        settings = AdapterFixtures.settingsAllowing(true);
        registry = new ScanCheckRegistry(catalogOf(insertionPointCheck, requestCheck, hostCheck),
                settings, McpEventLog.noop());
    }

    @Test
    void listsCatalogChecksInIdOrder() {
        assertThat(registry.all()).extracting(check -> check.descriptor().id())
                .containsExactly("a-host", "b-request", "c-ip");
    }

    @Test
    void catalogChecksAreAdaptedByKind() {
        assertThat(registry.all()).extracting(Object::getClass)
                .containsExactly(HostCheckAdapter.class, RequestCheckAdapter.class, InsertionPointCheckAdapter.class);
    }

    @Test
    void registersEveryCheckWithTheRegistrarUsingItsKindsScanCheckType() {
        ActiveCheckRegistrar registrar = mock(ActiveCheckRegistrar.class);

        registry.registerWith(registrar);

        verify(registrar).register((ActiveScanCheck) registry.all().get(0), ScanCheckType.PER_REQUEST);
        verify(registrar).register((ActiveScanCheck) registry.all().get(1), ScanCheckType.PER_REQUEST);
        verify(registrar).register((ActiveScanCheck) registry.all().get(2), ScanCheckType.PER_INSERTION_POINT);
        verify(registrar, times(3)).register(any(ActiveScanCheck.class), any(ScanCheckType.class));
    }

    @Test
    void countsOnlyEnabledChecks() {
        when(settings.isEnabled(eq("b-request"), anyBoolean())).thenReturn(false);

        assertThat(registry.enabledActiveCheckCount()).isEqualTo(2);
    }

    @Test
    void clearingConnectionStateResetsHostChecks() {
        HostCheckAdapter hostAdapter = (HostCheckAdapter) registry.all().get(0);
        hostAdapter.doCheck(AdapterFixtures.baseOn("a.test"), null, mock(Http.class));

        registry.clearConnectionState();
        hostAdapter.doCheck(AdapterFixtures.baseOn("a.test"), null, mock(Http.class));

        assertThat(hostCheck.calls).hasSize(2);
    }

    @Test
    void rejectsMissingCollaborators() {
        CheckCatalog empty = catalogOf();
        assertThatThrownBy(() -> new ScanCheckRegistry(null, settings, McpEventLog.noop()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanCheckRegistry(empty, null, McpEventLog.noop()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void issuesBurpWouldDropAfterAStreamingRefusalGoToTheRegistrysDestination() {
        AuditIssue issue = mock(AuditIssue.class);
        List<AuditIssue> delivered = new ArrayList<>();
        ScanCheckRegistry withDestination = new ScanCheckRegistry(catalogOf(new StreamedReplyCheck(issue)), settings,
                McpEventLog.noop(), CheckInstrumentation.NONE, UpstreamExchanges.NONE,
                ConnectionReuse.withoutLocalAnswers(), (check, issues, scanId) -> delivered.addAll(issues));
        Http refusing = mock(Http.class, invocation -> {
            throw new RuntimeException(new StreamingRefusalStandIn());
        });

        AuditResult result = ((ActiveScanCheck) withDestination.all().get(0))
                .doCheck(AdapterFixtures.baseOn("a.test"), null, refusing);

        assertThat(result.auditIssues()).isEmpty();
        assertThat(delivered).containsExactly(issue);
    }

    private static CheckCatalog catalogOf(McpCheck... checks) {
        return CheckCatalog.of(Arrays.stream(checks)
                .map(check -> (CheckFactory) services -> check)
                .toList(), CheckServices.standalone());
    }

    private record StreamedReplyCheck(AuditIssue issue) implements RequestCheck {

        @Override
        public CheckDescriptor descriptor() {
            return RecordingChecks.descriptorFor("streamed-reply", CheckKind.REQUEST);
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            try {
                http.sendRequest(HttpRequest.httpRequestFromUrl("https://a.test/mcp"));
            } catch (RuntimeException recovered) {
                assertThat(recovered).isNotNull();
            }
            return List.of(issue);
        }
    }
}
