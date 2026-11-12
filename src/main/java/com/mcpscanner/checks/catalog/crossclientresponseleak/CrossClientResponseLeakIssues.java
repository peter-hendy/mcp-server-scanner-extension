package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.issue.IssueBodyBuilder;
import com.mcpscanner.checks.issue.IssueMetadataRenderer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class CrossClientResponseLeakIssues {

    static final String ISSUE_NAME = "MCP response delivered to another client";

    private static final String EVIDENCE_SCOPE = "Each client was a separate MCP session opened by the scanner "
            + "with its own initialize handshake and the same configured credentials. Before racing, two "
            + "sequential clients each received only their own marker. In the crossed round the recipient's reply "
            + "lacked its own marker and carried another client's, and that other client did not receive its own "
            + "reply. Each same-id round was attempted up to three times and counted as crossed if any attempt "
            + "crossed, and every same-id round crossed. When the same race was repeated with a distinct "
            + "JSON-RPC id per client, every client got its own reply in all three attempts of every such round, "
            + "so the server routes replies by request id across clients.";
    private static final String REMEDIATION = "Route every reply to the connection or session that sent the "
            + "request, never by JSON-RPC id alone. Create a separate transport and protocol (server) instance "
            + "per stateless request or per session, and upgrade MCP SDKs that refuse shared instances.";

    private final CheckDescriptor descriptor;

    CrossClientResponseLeakIssues(CheckDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    Optional<AuditIssue> issueFor(TrialOutcome outcome, String baseUrl) {
        return outcome.crossing().map(crossing -> AuditIssue.auditIssue(ISSUE_NAME, detail(outcome, crossing),
                REMEDIATION, baseUrl, descriptor.headlineSeverity(), descriptor.headlineConfidence(),
                IssueMetadataRenderer.background(descriptor.issueBackground(), descriptor.cwes(),
                        descriptor.references()),
                null, descriptor.headlineSeverity(), evidence(crossing)));
    }

    private static String detail(TrialOutcome outcome, Crossing crossing) {
        return new IssueBodyBuilder()
                .paragraph("Several independent clients sent the same tool call at the same time with the same "
                        + "JSON-RPC id. Each put a unique marker in the echoed argument named " + outcome.argument() + ".")
                .findings(List.of(
                        "Recipient's own marker: " + crossing.recipient().marker(),
                        "Marker found in the recipient's reply instead: " + crossing.owner().marker()))
                .paragraph(EVIDENCE_SCOPE)
                .build();
    }

    private static List<HttpRequestResponse> evidence(Crossing crossing) {
        return Stream.of(crossing.recipient(), crossing.owner())
                .flatMap(reply -> reply.exchange().stream())
                .toList();
    }
}
