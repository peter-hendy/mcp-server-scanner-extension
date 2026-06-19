package com.mcpscanner.checks;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.checks.ToolArgRcePayloads.RcePayloadTemplate;
import com.mcpscanner.checks.ToolsCallRceProbeRunner.FiredProbe;
import com.mcpscanner.checks.ToolsListDiscovery.DiscoveredTool;
import com.mcpscanner.checks.issue.Cwe;
import com.mcpscanner.checks.registry.CheckDescriptor;
import com.mcpscanner.checks.registry.ManagedActiveCheck;
import com.mcpscanner.checks.registry.ScanCheckLogging;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.checks.registry.SessionScopedCheck;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.scan.ScanInventory;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Fires out-of-band code-execution probes at code-hinted tool arguments and registers each
 * minted Collaborator payload with the shared {@link CollaboratorPoller}, which reports the
 * confirmed issue asynchronously once the matching interaction arrives. The scanner thread
 * never blocks polling Collaborator — per Burp's recommended single-shared-client,
 * scheduled-poller integration model.
 */
public class McpActiveToolArgumentRceCheck extends ManagedActiveCheck
        implements SessionScopedCheck {

    private static final String ISSUE_NAME = "MCP Tool Argument Code Execution";
    static final int MAX_PROBES_PER_SCAN = 100;

    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "tool-arg-rce",
            ISSUE_NAME,
            "A string argument passed to a tool reaches a server-side code evaluator, giving a "
                    + "client confirmed arbitrary code execution under the server's runtime identity.",
            AuditIssueSeverity.HIGH,
            ScanCheckType.PER_REQUEST,
            true,
            List.of(
                    "https://owasp.org/www-community/attacks/Code_Injection",
                    "https://nvd.nist.gov/vuln/detail/CVE-2025-49596",
                    "https://nvd.nist.gov/vuln/detail/CVE-2025-6514"
            ),
            "A tool argument value reaches a shell or interpreter, allowing out-of-band command or "
                    + "code execution (confirmed via Burp Collaborator).",
            List.of(
                    new Cwe(77, "Improper Neutralization of Special Elements used in a Command "
                            + "('Command Injection')"),
                    new Cwe(94, "Improper Control of Generation of Code ('Code Injection')"))
    );

    private final CollaboratorPoller poller;
    private final Supplier<ScanInventory> selectedInventorySupplier;
    private final HostDedup hostDedup = new HostDedup();

    public McpActiveToolArgumentRceCheck(ScanCheckSettings settings,
                                         McpEventLog eventLog,
                                         CollaboratorPoller poller,
                                         Supplier<ScanInventory> selectedInventorySupplier) {
        super(settings, eventLog);
        this.poller = poller;
        this.selectedInventorySupplier = selectedInventorySupplier != null
                ? selectedInventorySupplier
                : ScanInventory::empty;
    }

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void clearSessionState() {
        hostDedup.clear();
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue existingIssue, AuditIssue newIssue) {
        return consolidateByName(ISSUE_NAME, existingIssue, newIssue);
    }

    @Override
    protected AuditResult runCheck(HttpRequestResponse baseRequestResponse,
                                   AuditInsertionPoint insertionPoint, Http http) {
        if (!McpRequestDetector.classify(baseRequestResponse).isMcp()) {
            return emptyResult();
        }
        // PER_REQUEST dispatch fires this self-discovering check once per insertion point; the
        // Collaborator-backed probe battery only needs to run once per host. The result is
        // credential-independent (an injectable evaluator fires regardless of bearer), so a plain
        // host key is sufficient.
        if (!hostDedup.tryClaim(baseRequestResponse.request())) {
            ScanCheckLogging.decisionSkipped(eventLog(), descriptor().id(), "already probed host");
            return emptyResult();
        }

        Set<String> selectedToolNames = SelectedToolsFilter.selectedToolNames(selectedInventorySupplier);
        if (selectedToolNames.isEmpty()) {
            logSkippedNoSelection();
            return emptyResult();
        }

        HttpRequest baseline = baseRequestResponse.request();
        ReachabilityTrackingHttp trackedHttp = new ReachabilityTrackingHttp(http);
        ToolsCallRceProbeRunner runner = new ToolsCallRceProbeRunner(trackedHttp);
        List<DiscoveredTool> selectedTools =
                SelectedToolsFilter.retainSelected(runner.discoverTools(baseline), selectedToolNames);
        if (selectedTools.isEmpty()) {
            releaseIfUnreached(baseRequestResponse, trackedHttp);
            return emptyResult();
        }
        List<ToolArgument> codeArguments = runner.findCodeArguments(selectedTools);
        if (codeArguments.isEmpty()) {
            return emptyResult();
        }

        CollaboratorClient client = poller != null ? poller.sharedClient() : null;
        if (client == null) {
            // Collaborator being unavailable is a transient/environmental condition, not a clean
            // negative from the server — release so a later insertion point retries once it returns.
            hostDedup.releaseIfHttpLayerErrored(baseRequestResponse.request());
            return emptyResult();
        }

        String baseUrl = McpRequestDetector.extractBaseUrl(baseRequestResponse);
        int firedProbes = fireAndRegisterProbes(runner, baseline, baseUrl, codeArguments, client);
        if (firedProbes == 0) {
            releaseIfUnreached(baseRequestResponse, trackedHttp);
        }
        // The OOB result, if any, is reported asynchronously by the CollaboratorPoller once the
        // matching interaction arrives — never synchronously on the scanner thread.
        return emptyResult();
    }

    private void releaseIfUnreached(HttpRequestResponse baseRequestResponse,
                                    ReachabilityTrackingHttp trackedHttp) {
        // Release the claim only when the battery never reached the server (every probe failed at
        // the HTTP layer) so a later insertion point retries; a clean negative from a reachable
        // server keeps the claim so the battery does not re-run per insertion point.
        if (!trackedHttp.reachedServer()) {
            hostDedup.releaseIfHttpLayerErrored(baseRequestResponse.request());
        }
    }

    private void logSkippedNoSelection() {
        McpEventLog log = eventLog();
        if (log != null) {
            log.info("tool-arg-rce: no tools selected by user, skipping");
        }
    }

    private int fireAndRegisterProbes(ToolsCallRceProbeRunner runner,
                                      HttpRequest baseline,
                                      String baseUrl,
                                      List<ToolArgument> codeArguments,
                                      CollaboratorClient client) {
        int fired = 0;
        int templateCount = ToolArgRcePayloads.all().size();
        int distinctTools = (int) codeArguments.stream().map(ToolArgument::tool).distinct().count();
        for (ToolArgument argument : codeArguments) {
            for (RcePayloadTemplate payload : ToolArgRcePayloads.all()) {
                if (fired >= MAX_PROBES_PER_SCAN) {
                    logProbeCapReached(distinctTools, codeArguments.size(), templateCount);
                    return fired;
                }
                CollaboratorPayload mintedPayload;
                try {
                    mintedPayload = client.generatePayload();
                } catch (RuntimeException ex) {
                    logPayloadGenerationFailed(ex);
                    return fired;
                }
                String subdomain = mintedPayload.toString();
                String payloadId = mintedPayload.id().toString();
                FiredProbe probe = runner.fire(baseline, argument, payload, subdomain, payloadId);
                poller.register(payloadId, new ProbeContext(
                        baseUrl, argument.tool().name(), argument.name(),
                        payload, subdomain, probe.response()));
                fired++;
            }
        }
        return fired;
    }

    private void logPayloadGenerationFailed(RuntimeException ex) {
        McpEventLog log = eventLog();
        if (log != null) {
            log.info("tool-arg-rce: payload generation failed ("
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    + "); aborting probe loop");
        }
    }

    private void logProbeCapReached(int distinctTools, int codeArguments, int templateCount) {
        McpEventLog log = eventLog();
        if (log != null) {
            log.info("tool-arg-rce: probe cap reached at " + MAX_PROBES_PER_SCAN
                    + ", skipping remaining (" + distinctTools + " tools x "
                    + codeArguments + " args x " + templateCount + " templates queued)");
        }
    }

    private static AuditResult emptyResult() {
        return AuditResult.auditResult(List.of());
    }
}
