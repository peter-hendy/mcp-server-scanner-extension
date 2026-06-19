package com.mcpscanner.checks;

import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionId;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.checks.registry.CheckDescriptor;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.CurrentSelectionHolder;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpActiveToolArgumentRceCheckTest {

    private static final String MCP_REQUEST_BODY = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\"}";

    private static final String TOOLS_LIST_WITH_CODE_ARG =
            "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"format_quote\","
                    + "\"description\":\"Render quote\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                    + "{\"format\":{\"type\":\"string\",\"description\":\"Python expression to evaluate.\"}},"
                    + "\"required\":[\"format\"]}}]}}";

    private static final String TOOLS_LIST_WITHOUT_CODE_ARG =
            "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"query_user\","
                    + "\"description\":\"Look up user\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                    + "{\"username\":{\"type\":\"string\",\"description\":\"User to find.\"}}}}]}}";

    private static final String TOOLS_LIST_TWO_CODE_TOOLS =
            "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":["
                    + "{\"name\":\"format_quote\",\"description\":\"Render quote\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                    + "{\"format\":{\"type\":\"string\",\"description\":\"Expression\"}}}},"
                    + "{\"name\":\"run_script\",\"description\":\"Run a script\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                    + "{\"script\":{\"type\":\"string\",\"description\":\"Script body\"}}}}"
                    + "]}}";

    private static final String TOOLS_LIST_ANYOF_CODE_ARG =
            "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"format_quote\","
                    + "\"description\":\"Render quote\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                    + "{\"format\":{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"null\"}],"
                    + "\"description\":\"Python expression to evaluate.\"}},"
                    + "\"required\":[\"format\"]}}]}}";

    private static final String EMPTY_TOOL_CALL_BODY =
            "{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}";

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    @Mock private HttpRequestResponse baseRequestResponse;
    @Mock private AuditInsertionPoint insertionPoint;
    @Mock private Http http;
    @Mock private HttpRequest request;
    @Mock private HttpService httpService;
    @Mock private ScanCheckSettings settings;
    @Mock private McpEventLog eventLog;
    @Mock private CollaboratorClient collaboratorClient;

    private InteractionIssuer issuer;
    private List<AuditIssue> reported;
    private CollaboratorPoller poller;
    private CurrentSelectionHolder selectionHolder;
    private McpActiveToolArgumentRceCheck check;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (poller != null) {
            poller.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        lenient().when(settings.isEnabled(anyString(), anyBoolean())).thenReturn(true);
        issuer = new InteractionIssuer();
        reported = new CopyOnWriteArrayList<>();
        selectionHolder = new CurrentSelectionHolder();
        poller = newPoller(() -> collaboratorClient);
        check = new McpActiveToolArgumentRceCheck(settings, eventLog, poller, selectionHolder);
    }

    private CollaboratorPoller newPoller(java.util.function.Supplier<CollaboratorClient> clientSupplier) {
        Collaborator collaborator = asCollaborator(clientSupplier);
        CollaboratorPoller created = new CollaboratorPoller(
                () -> collaborator, eventLog, reported::add, Duration.ofMillis(20));
        created.start();
        return created;
    }

    private void awaitReported(int expected) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(reported).hasSize(expected));
    }

    private static Collaborator asCollaborator(java.util.function.Supplier<CollaboratorClient> clientSupplier) {
        Collaborator collaborator = mock(Collaborator.class);
        lenient().when(collaborator.createClient()).thenAnswer(invocation -> clientSupplier.get());
        return collaborator;
    }

    private static McpToolDefinition toolNamed(String name) {
        return new McpToolDefinition(name, "", "{}");
    }

    private void selectToolsByName(String... names) {
        McpToolDefinition[] tools = new McpToolDefinition[names.length];
        for (int i = 0; i < names.length; i++) {
            tools[i] = toolNamed(names[i]);
        }
        selectionHolder.set(ScanInventory.toolsOnly(List.of(tools)));
    }

    @Test
    void descriptor_exposesToolArgRceMetadata() {
        CheckDescriptor descriptor = check.descriptor();

        assertThat(descriptor.id()).isEqualTo("tool-arg-rce");
        assertThat(descriptor.displayName()).isEqualTo("MCP Tool Argument Code Execution");
        assertThat(descriptor.headlineSeverity()).isEqualTo(AuditIssueSeverity.HIGH);
        assertThat(descriptor.scope()).isEqualTo(ScanCheckType.PER_REQUEST);
        assertThat(descriptor.defaultEnabled()).isTrue();
    }

    @Test
    void runCheckSendsProbesAndRegistersPayloadsButReportsNothingSynchronously() {
        // The OOB path is now asynchronous: runCheck fires the probes, registers each minted
        // payload with the poller, and returns an EMPTY result. No inline poll, no synchronous
        // issue. The matching interaction is reported later by the poller.
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        assertThat(issuer.generatedCount()).isEqualTo(ToolArgRcePayloads.all().size());
    }

    @Test
    void pollerReportsIssueWhenMatchingInteractionArrives() {
        // Drive the registered payloads through the poller: when a matching interaction shows up,
        // the "MCP Tool Argument Code Execution" issue is reported via the sink with the right
        // evidence and confidence.
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        check.doCheck(baseRequestResponse, insertionPoint, http);
        issuer.recordInteractionFor(0);
        awaitReported(1);

        AuditIssue issue = reported.get(0);
        assertThat(issue.name()).isEqualTo("MCP Tool Argument Code Execution");
        assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        assertThat(issue.detail()).contains("format_quote::format");
        assertThat(issue.detail()).contains("Node.js payload triggered a DNS callback");
        assertThat(issue.detail()).contains(".lookup(");
        assertThat(issue.detail()).contains("oastify.example");
        assertThat(issue.detail()).contains("Only a DNS lookup");
        assertThat(issue.detail()).contains("Firm rather than Certain");
        assertThat(issue.detail()).doesNotContain("NODE_DNS_LOOKUP");
    }

    @Test
    void httpInteractionYieldsCertainConfidence() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        check.doCheck(baseRequestResponse, insertionPoint, http);
        issuer.recordInteractionFor(0, InteractionType.HTTP);
        awaitReported(1);

        AuditIssue issue = reported.get(0);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.CERTAIN);
        assertThat(issue.detail()).contains("full HTTP callback");
        assertThat(issue.detail()).contains("confirmed arbitrary code execution");
    }

    @Test
    void noInteractionMeansNoIssue() throws InterruptedException {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        check.doCheck(baseRequestResponse, insertionPoint, http);
        Thread.sleep(80);

        assertThat(reported).isEmpty();
    }

    @Test
    void dedupsRepeatedInsertionPointsAgainstSameHost() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        check.doCheck(baseRequestResponse, insertionPoint, http);
        int payloadsAfterFirst = issuer.generatedCount();
        check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(issuer.generatedCount())
                .as("second insertion point must not re-mint Collaborator payloads")
                .isEqualTo(payloadsAfterFirst);
    }

    @Test
    void clearSessionStateAllowsReprobeAfterReconnect() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());

        check.doCheck(baseRequestResponse, insertionPoint, http);
        int firstBatch = issuer.generatedCount();
        check.clearSessionState();
        check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(issuer.generatedCount()).isGreaterThan(firstBatch);
    }

    @Test
    void transientHttpLayerErrorReleasesClaimSoNextInsertionPointReprobes() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        boolean[] firstAttempt = {true};
        stubResponses(body -> {
            if (firstAttempt[0]) {
                return transientFailure();
            }
            return rceResponses().apply(body);
        });

        check.doCheck(baseRequestResponse, insertionPoint, http);
        int afterFirst = issuer.generatedCount();
        firstAttempt[0] = false;
        check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(afterFirst).isZero();
        assertThat(issuer.generatedCount()).isPositive();
    }

    @Test
    void transientCollaboratorUnavailabilityReleasesClaimSoNextInsertionPointReprobes() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(rceResponses());
        boolean[] collaboratorDown = {true};
        poller.shutdown();
        // A fresh poller whose first sharedClient() resolves null (Collaborator down), then the
        // stub once it returns; mirrors a transient Collaborator hiccup mid-scan.
        poller = newPoller(() -> collaboratorDown[0] ? null : collaboratorClient);
        check = new McpActiveToolArgumentRceCheck(settings, eventLog, poller, selectionHolder);

        check.doCheck(baseRequestResponse, insertionPoint, http);
        int afterFirst = issuer.generatedCount();
        collaboratorDown[0] = false;
        check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(afterFirst).isZero();
        assertThat(issuer.generatedCount()).isPositive();
    }

    @Test
    void doCheck_returnsEmptyWhenDisabled() {
        when(settings.isEnabled("tool-arg-rce", true)).thenReturn(false);

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        verify(http, never()).sendRequest(any(HttpRequest.class));
    }

    @Test
    void skipsAndLogsWhenSharedClientUnavailable() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubResponses(body -> successBody(TOOLS_LIST_WITH_CODE_ARG));
        poller.shutdown();
        poller = newPoller(() -> null);
        check = new McpActiveToolArgumentRceCheck(settings, eventLog, poller, selectionHolder);

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        assertThat(issuer.generatedCount()).isZero();
    }

    @Test
    void degradesGracefullyWhenPollerIsNull() {
        // Collaborator-unavailable editions wire a null poller. The check must no-op cleanly.
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubResponses(body -> successBody(TOOLS_LIST_WITH_CODE_ARG));
        check = new McpActiveToolArgumentRceCheck(settings, eventLog, null, selectionHolder);

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
    }

    @Test
    void doesNotFireProbesWhenNoToolsHaveCodeHintedArgs() {
        selectToolsByName("query_user");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(body -> successBody(TOOLS_LIST_WITHOUT_CODE_ARG));

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        assertThat(issuer.generatedCount()).isZero();
    }

    @Test
    void reportsSeparateIssuesPerToolArgument() {
        selectToolsByName("format_quote", "run_script");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(body -> {
            if (body.contains("tools/list")) {
                return successBody(TOOLS_LIST_TWO_CODE_TOOLS);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        });

        check.doCheck(baseRequestResponse, insertionPoint, http);
        issuer.recordInteractionFor(0);
        issuer.recordInteractionFor(ToolArgRcePayloads.all().size());
        awaitReported(2);

        assertThat(reported).extracting(AuditIssue::detail)
                .anyMatch(detail -> detail.contains("format_quote::format"))
                .anyMatch(detail -> detail.contains("run_script::script"));
    }

    @Test
    void capsCollaboratorPayloadsAtConfiguredMaximum() {
        String[] manyToolNames = new String[12];
        for (int i = 0; i < manyToolNames.length; i++) {
            manyToolNames[i] = "tool_" + i;
        }
        selectToolsByName(manyToolNames);
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        String manyToolsBody = manyCodeToolsBody(12);
        stubResponses(body -> {
            if (body.contains("tools/list")) {
                return successBody(manyToolsBody);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        });

        check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(issuer.generatedCount())
                .isEqualTo(McpActiveToolArgumentRceCheck.MAX_PROBES_PER_SCAN);
        verify(eventLog).info(org.mockito.ArgumentMatchers.contains("probe cap reached at "
                + McpActiveToolArgumentRceCheck.MAX_PROBES_PER_SCAN));
    }

    @Test
    void doesNotFireForNonMcpRequest() {
        when(baseRequestResponse.request()).thenReturn(request);
        when(request.method()).thenReturn("POST");
        when(request.bodyToString()).thenReturn("{\"hello\":\"world\"}");

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        verify(http, never()).sendRequest(any(HttpRequest.class));
    }

    @Test
    void doesNotMintCollaboratorClientWhenNoToolsSelected() {
        stubMcpBaselineRequest();

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        assertThat(issuer.generatedCount()).isZero();
        verify(http, never()).sendRequest(any(HttpRequest.class));
        verify(eventLog).info(org.mockito.ArgumentMatchers.contains("no tools selected"));
    }

    @Test
    void filtersDiscoveredToolsToUserSelectionBeforeMintingPayloads() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(body -> {
            if (body.contains("tools/list")) {
                return successBody(TOOLS_LIST_TWO_CODE_TOOLS);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        });

        check.doCheck(baseRequestResponse, insertionPoint, http);
        issuer.recordInteractionFor(0);
        awaitReported(1);

        assertThat(reported.get(0).detail()).contains("format_quote::format");
        assertThat(reported.get(0).detail()).doesNotContain("run_script");
        assertThat(issuer.generatedCount()).isEqualTo(ToolArgRcePayloads.all().size());
        verify(http, never()).sendRequest(org.mockito.ArgumentMatchers.argThat(
                req -> req != null && req.bodyToString().contains("\"run_script\"")));
    }

    @Test
    void failsClosedWhenGeneratePayloadThrows() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubResponses(body -> {
            if (body.contains("tools/list")) {
                return successBody(TOOLS_LIST_WITH_CODE_ARG);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        });
        when(collaboratorClient.generatePayload())
                .thenThrow(new RuntimeException("collaborator quota exhausted"));

        AuditResult result = check.doCheck(baseRequestResponse, insertionPoint, http);

        assertThat(result.auditIssues()).isEmpty();
        verify(collaboratorClient, times(1)).generatePayload();
        verify(collaboratorClient, never()).getAllInteractions();
        verify(eventLog).info(org.mockito.ArgumentMatchers.contains("payload generation failed"));
    }

    @Test
    void firesForAnyOfStringCodeArgumentSchema() {
        selectToolsByName("format_quote");
        stubMcpBaselineRequest();
        stubCollaboratorClient();
        stubResponses(body -> {
            if (body.contains("tools/list")) {
                return successBody(TOOLS_LIST_ANYOF_CODE_ARG);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        });

        check.doCheck(baseRequestResponse, insertionPoint, http);
        issuer.recordInteractionFor(0);
        awaitReported(1);

        assertThat(reported.get(0).detail()).contains("format_quote::format");
    }

    private Function<String, HttpRequestResponse> rceResponses() {
        return body -> {
            if (body.contains("tools/list")) {
                return successBody(TOOLS_LIST_WITH_CODE_ARG);
            }
            return successBody(EMPTY_TOOL_CALL_BODY);
        };
    }

    private void stubMcpBaselineRequest() {
        when(baseRequestResponse.request()).thenReturn(request);
        when(request.method()).thenReturn("POST");
        when(request.bodyToString()).thenReturn(MCP_REQUEST_BODY);
        lenient().when(request.httpService()).thenReturn(httpService);
        lenient().when(httpService.secure()).thenReturn(false);
        lenient().when(httpService.host()).thenReturn("localhost");
        lenient().when(httpService.port()).thenReturn(8080);
    }

    private void stubResponses(Function<String, HttpRequestResponse> responseFn) {
        when(request.withBody(anyString())).thenAnswer(invocation -> requestWithBody(invocation.getArgument(0)));
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> {
            HttpRequest sent = invocation.getArgument(0);
            return responseFn.apply(sent.bodyToString());
        });
    }

    private void stubCollaboratorClient() {
        lenient().when(collaboratorClient.generatePayload()).thenAnswer(invocation -> issuer.mintPayload());
        lenient().when(collaboratorClient.getAllInteractions()).thenAnswer(invocation -> issuer.interactions());
    }

    private static String manyCodeToolsBody(int toolCount) {
        StringBuilder body = new StringBuilder("{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[");
        for (int i = 0; i < toolCount; i++) {
            if (i > 0) {
                body.append(",");
            }
            body.append("{\"name\":\"tool_").append(i).append("\",")
                    .append("\"description\":\"Tool ").append(i).append("\",")
                    .append("\"inputSchema\":{\"type\":\"object\",\"properties\":{")
                    .append("\"script\":{\"type\":\"string\",\"description\":\"Script.\"}}}}");
        }
        body.append("]}}");
        return body.toString();
    }

    private static HttpRequest requestWithBody(String body) {
        HttpRequest mutated = mock(HttpRequest.class);
        lenient().when(mutated.bodyToString()).thenReturn(body);
        return mutated;
    }

    private static HttpRequestResponse successBody(String responseBody) {
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        HttpResponse response = mock(HttpResponse.class);
        lenient().when(rr.response()).thenReturn(response);
        lenient().when(response.statusCode()).thenReturn((short) 200);
        lenient().when(response.bodyToString()).thenReturn(responseBody);
        return rr;
    }

    private static HttpRequestResponse transientFailure() {
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        lenient().when(rr.response()).thenReturn(null);
        return rr;
    }

    private static final class InteractionIssuer {
        private final List<String> issuedIds = new ArrayList<>();
        private final Map<Integer, InteractionType> scheduledInteractionIndices = new LinkedHashMap<>();

        CollaboratorPayload mintPayload() {
            String id = "iid-" + issuedIds.size();
            issuedIds.add(id);
            CollaboratorPayload payload = mock(CollaboratorPayload.class);
            InteractionId payloadId = mock(InteractionId.class);
            lenient().when(payloadId.toString()).thenReturn(id);
            lenient().when(payload.id()).thenReturn(payloadId);
            lenient().when(payload.toString()).thenReturn(id + ".oastify.example");
            return payload;
        }

        void recordInteractionFor(int payloadIndex) {
            recordInteractionFor(payloadIndex, InteractionType.DNS);
        }

        void recordInteractionFor(int payloadIndex, InteractionType type) {
            scheduledInteractionIndices.put(payloadIndex, type);
        }

        List<Interaction> interactions() {
            List<Interaction> resolved = new ArrayList<>(scheduledInteractionIndices.size());
            for (Map.Entry<Integer, InteractionType> entry : scheduledInteractionIndices.entrySet()) {
                int index = entry.getKey();
                if (index < issuedIds.size()) {
                    resolved.add(mockInteraction(issuedIds.get(index), entry.getValue()));
                }
            }
            return resolved;
        }

        int generatedCount() {
            return issuedIds.size();
        }

        private static Interaction mockInteraction(String id, InteractionType type) {
            Interaction interaction = mock(Interaction.class);
            InteractionId interactionId = mock(InteractionId.class);
            lenient().when(interactionId.toString()).thenReturn(id);
            lenient().when(interaction.id()).thenReturn(interactionId);
            lenient().when(interaction.type()).thenReturn(type);
            return interaction;
        }
    }
}
