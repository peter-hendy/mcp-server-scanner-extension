package com.mcpscanner.checks;

import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.Cwe;
import com.mcpscanner.checks.issue.IssueBodyBuilder;
import com.mcpscanner.checks.issue.IssueMetadataRenderer;

import java.util.List;

/**
 * Builds the "MCP Tool Argument Code Execution" {@link AuditIssue} for a single confirmed
 * out-of-band hit (a {@link ProbeContext} correlated with the Collaborator {@link Interaction}
 * its payload triggered). Shared by {@link CollaboratorPoller} so issue wording, severity,
 * confidence, CWEs and references live in one place.
 */
public final class RceIssueReporter {

    static final String ISSUE_NAME = "MCP Tool Argument Code Execution";

    private static final String ISSUE_BACKGROUND =
            "A tool argument value reaches a shell or interpreter, allowing out-of-band command or "
                    + "code execution (confirmed via Burp Collaborator).";

    private static final List<String> REFERENCES = List.of(
            "https://owasp.org/www-community/attacks/Code_Injection",
            "https://nvd.nist.gov/vuln/detail/CVE-2025-49596",
            "https://nvd.nist.gov/vuln/detail/CVE-2025-6514");

    private static final List<Cwe> CWES = List.of(
            new Cwe(77, "Improper Neutralization of Special Elements used in a Command "
                    + "('Command Injection')"),
            new Cwe(94, "Improper Control of Generation of Code ('Code Injection')"));

    private static final String REMEDIATION = new IssueBodyBuilder()
            .paragraph("Never pass tool arguments into language-level evaluators (eval, exec, "
                    + "Function, system, backticks, child_process, Kernel#eval). Parse them against "
                    + "an explicit grammar or allow-list.")
            .paragraph("If dynamic evaluation is genuinely required, run it in a sandboxed process "
                    + "with no network, no filesystem, and a strict time limit, and validate the "
                    + "input against a constrained schema before invocation.")
            .build();

    private RceIssueReporter() {}

    public static AuditIssue buildIssue(ProbeContext context, Interaction interaction) {
        return AuditIssue.auditIssue(
                ISSUE_NAME,
                renderDetail(context, interaction),
                REMEDIATION,
                context.baseUrl(),
                AuditIssueSeverity.HIGH, confidenceFor(interaction),
                IssueMetadataRenderer.background(ISSUE_BACKGROUND, CWES, REFERENCES),
                null, AuditIssueSeverity.HIGH,
                List.of(context.probeResponse()));
    }

    // A full HTTP callback to the unique payload host is unequivocal code execution (CERTAIN).
    // A DNS-only lookup is strong but has narrow alternative explanations (shared resolver, DNS
    // prefetch), so it caps the confidence at FIRM.
    private static AuditIssueConfidence confidenceFor(Interaction interaction) {
        return interaction.type() == InteractionType.HTTP
                ? AuditIssueConfidence.CERTAIN
                : AuditIssueConfidence.FIRM;
    }

    private static String renderDetail(ProbeContext context, Interaction interaction) {
        String hitLine = context.payload().language() + " payload triggered a "
                + callbackKind(interaction.type()) + " callback: "
                + context.payload().render(context.collaboratorSubdomain());
        return new IssueBodyBuilder()
                .paragraph("A string argument passed to this tool was executed as server-side code. "
                        + "The server made an out-of-band callback to a scanner-controlled host, "
                        + "evidencing code execution under the server's runtime identity.")
                .paragraph(evidenceStrengthNote(interaction))
                .paragraph("Tool argument: " + context.issueKey())
                .findings(List.of(hitLine))
                .build();
    }

    private static String evidenceStrengthNote(Interaction interaction) {
        if (interaction.type() == InteractionType.HTTP) {
            return "A full HTTP callback reached the unique payload host, so this is confirmed "
                    + "arbitrary code execution.";
        }
        return "Only a DNS lookup of the unique payload host was observed (no HTTP callback). "
                + "This is strong evidence of code execution, but a DNS-only interaction has narrow "
                + "alternative explanations (a shared resolver or DNS prefetch), so the confidence is "
                + "reported as Firm rather than Certain.";
    }

    private static String callbackKind(InteractionType type) {
        return switch (type) {
            case DNS -> "DNS";
            case HTTP -> "HTTP";
            default -> "network";
        };
    }
}
