package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.collaborator.FakeCollaborator;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.mcpscanner.checks.catalog.outboundrequest.FetchingTarget.bodyContains;
import static com.mcpscanner.checks.catalog.outboundrequest.OutboundFixtures.exchange;
import static com.mcpscanner.checks.catalog.outboundrequest.OutboundFixtures.field;
import static com.mcpscanner.checks.catalog.outboundrequest.OutboundFixtures.mcpRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboundRequestSkipLoggingTest {

    private static final String URL_FIELD = "MCP field: params.arguments.url";
    private static final String PROGRESS_TOKEN = "MCP field: params._meta.progressToken";
    private static final String TOOL_CALL_SUBJECT = "tools/call render_report at " + OutboundFixtures.URL;

    private final FakeCollaborator collaborator = new FakeCollaborator();
    private final FetchingTarget target = new FetchingTarget(collaborator);
    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void logsOncePerLoadThatCollaboratorIsUnavailable() {
        SettledOutboundCheck check = checkWith(Optional.empty());
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);

        check.audit(exchange(toolCall), field(toolCall, URL_FIELD), target.http());
        check.audit(exchange(toolCall), field(toolCall, URL_FIELD), target.http());

        assertThat(lines()).containsExactly("skipped field " + URL_FIELD + " of " + TOOL_CALL_SUBJECT
                + " (prerequisite missing: Burp Collaborator is unavailable)");
    }

    @Test
    void logsAFieldTheServerCannotFetchFromAsNotApplicable() {
        HttpRequest plain = OutboundFixtures.jsonRequest("http://app.example.test/search", OutboundFixtures.PLAIN_JSON);
        AuditInsertionPoint builtIn = mock(AuditInsertionPoint.class);
        when(builtIn.name()).thenReturn("query");
        when(builtIn.type()).thenReturn(AuditInsertionPointType.PARAM_JSON);

        checkWith(Optional.of(collaborator)).audit(exchange(plain), builtIn, target.http());

        assertThat(lines()).singleElement().asString()
                .startsWith("skipped field query of ")
                .endsWith(" (not applicable: not an MCP extension field or an OAuth fetched-URL parameter)");
    }

    @Test
    void logsAFieldAlreadyConfirmedAtThisEndpointAtInfo() {
        target.fetchingWhen(bodyContains("\"progressToken\":\"http://"));
        SettledOutboundCheck check = checkWith(Optional.of(collaborator));
        List<String> methods = OutboundFixtures.METHODS_WITH_PROGRESS_TOKEN;
        HttpRequest first = mcpRequest(methods.get(0));
        HttpRequest second = mcpRequest(methods.get(1));
        check.audit(exchange(first), field(first, PROGRESS_TOKEN), target.http());

        check.audit(exchange(second), field(second, PROGRESS_TOKEN), target.http());

        assertThat(infoLines()).anySatisfy(line -> assertThat(line)
                .startsWith("skipped field " + PROGRESS_TOKEN + " of ")
                .endsWith(" (already audited on this connection: an outbound request from this field is "
                        + "already confirmed at this endpoint)"));
    }

    @Test
    void stopsAFieldAtTheFirstHttp429AndWatchesNothing() {
        target.rateLimiting().fetchingWhen(bodyContains(FakeCollaborator.DOMAIN));
        SettledOutboundCheck check = checkWith(Optional.of(collaborator));
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);

        assertThat(check.audit(exchange(toolCall), field(toolCall, URL_FIELD), target.http())).isEmpty();

        assertThat(target.received()).hasSize(1);
        assertThat(lines()).containsExactly("stopped field " + URL_FIELD + " of " + TOOL_CALL_SUBJECT
                + " (rate limited: the server answered HTTP 429)");
    }

    @Test
    void stillWatchesThePayloadsSentBeforeAnHttp429() {
        target.rateLimitingAfter(1).fetchingWhen(bodyContains(FakeCollaborator.DOMAIN));
        SettledOutboundCheck check = checkWith(Optional.of(collaborator));
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);

        List<AuditIssue> issues = check.audit(exchange(toolCall), field(toolCall, URL_FIELD), target.http());

        assertThat(issues).isNotEmpty();
        assertThat(lines()).contains("stopped field " + URL_FIELD + " of " + TOOL_CALL_SUBJECT
                + " (rate limited: the server answered HTTP 429)");
    }

    @Test
    void logsAFieldStoppedWhenCollaboratorIssuesNoPayload() {
        FakeCollaborator failing = new FakeCollaborator() {
            @Override
            public burp.api.montoya.collaborator.CollaboratorPayload generatePayload(
                    burp.api.montoya.collaborator.PayloadOption... options) {
                throw new IllegalStateException("Collaborator disabled");
            }
        };
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);

        checkWith(Optional.of(failing)).audit(exchange(toolCall), field(toolCall, URL_FIELD), target.http());

        assertThat(target.received()).isEmpty();
        assertThat(lines()).containsExactly("skipped field " + URL_FIELD + " of " + TOOL_CALL_SUBJECT
                + " (prerequisite missing: Burp Collaborator issued no payload)");
    }

    private SettledOutboundCheck checkWith(Optional<CollaboratorClient> client) {
        return SettledOutboundCheck.over(client, eventLog);
    }

    private List<String> lines() {
        return messages(McpEventLog.Level.INFO).stream()
                .filter(line -> line.startsWith("skipped ") || line.startsWith("stopped "))
                .toList();
    }

    private List<String> infoLines() {
        return messages(McpEventLog.Level.INFO);
    }

    private List<String> messages(McpEventLog.Level level) {
        String prefix = CheckLog.prefix(OutboundRequestDescriptor.DESCRIPTOR.id());
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == level)
                .map(McpEventLog.LogEntry::message)
                .filter(message -> message.startsWith(prefix))
                .map(message -> message.substring(prefix.length()))
                .toList();
    }
}
