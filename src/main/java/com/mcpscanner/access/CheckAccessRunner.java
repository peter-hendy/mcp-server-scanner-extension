package com.mcpscanner.access;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.access.AccessComparison;
import com.mcpscanner.access.AccessRunStopped;
import com.mcpscanner.access.AccessComparisonRun;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.RetryAfterHonouringHttp;
import com.mcpscanner.checks.api.RetryAfterWaits;
import com.mcpscanner.checks.shared.scancontext.ScanNotices;
import com.mcpscanner.http.CountingHttp;
import com.mcpscanner.http.ThreadTallies;
import com.mcpscanner.http.UpstreamEvidence;
import com.mcpscanner.http.UpstreamExchanges;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CheckAccessRunner {

    private final AccessComparison comparison;
    private final Http http;
    private final OwnerRequests requests;
    private final Consumer<AuditIssue> issueSink;
    private final WriteReplays writeReplays;
    private final AccessRuns runs;
    private final McpEventLog eventLog;
    private final UpstreamEvidence upstreamEvidence;

    public CheckAccessRunner(AccessComparison comparison, Http http, OwnerRequests requests,
                             Consumer<AuditIssue> issueSink, WriteReplays writeReplays, AccessRuns runs,
                             McpEventLog eventLog) {
        this(comparison, http, requests, issueSink, writeReplays, runs, eventLog, UpstreamExchanges.NONE);
    }

    public CheckAccessRunner(AccessComparison comparison, Http http, OwnerRequests requests,
                             Consumer<AuditIssue> issueSink, WriteReplays writeReplays, AccessRuns runs,
                             McpEventLog eventLog, UpstreamExchanges upstreamExchanges) {
        this.upstreamEvidence = new UpstreamEvidence(upstreamExchanges);
        this.comparison = comparison;
        this.http = http;
        this.requests = requests;
        this.issueSink = issueSink;
        this.writeReplays = writeReplays;
        this.runs = runs;
        this.eventLog = eventLog;
    }

    public Optional<AccessRun> start(List<AccessRow> rows, WriteReplayApproval approval, AccessRunListener listener) {
        List<AccessRow> writeRows = writeRows(rows);
        List<McpToolDefinition> writes = requests.ownersTools(writeRows);
        if (!writes.isEmpty() && !approval.approve(writes, requests.minedArguments(writeRows))) {
            eventLog.info("Check access cancelled at the write-tool confirmation");
            return Optional.empty();
        }
        writeReplays.confirm(writes);
        List<AccessRow> queued = List.copyOf(rows);
        return Optional.of(runs.start(run -> runAll(queued, run, listener)));
    }

    private void runAll(List<AccessRow> rows, AccessRun run, AccessRunListener listener) {
        eventLog.info("Check access started for " + rows.size() + " items");
        TooManyRequestsStop guarded = guarded(run);
        ThreadTallies perItem = new ThreadTallies();
        Http counted = CountingHttp.counting(guarded, perItem);
        RepeatedFailure failures = new RepeatedFailure();
        RunTally tally = new RunTally(rows.size(), listener);
        try (AccessComparisonRun comparisons = comparison.begin(counted)) {
            ParallelItems.run(rows, row -> perItem.measure("Check access " + row.item().key().name(),
                            eventLog::debug, () -> checkRow(row, guarded, counted, comparisons)),
                    (row, outcome) -> tally.completed(outcome, stopReasonAfter(row, outcome, failures, guarded)),
                    () -> !run.cancelled() && !tally.stopped());
        }
        AccessRunSummary summary = tally.summary(run.cancelled() || Thread.currentThread().isInterrupted());
        logEnd(summary, tally.stopReason());
        listener.finished(summary);
    }

    private TooManyRequestsStop guarded(AccessRun run) {
        AtomicBoolean stopped = new AtomicBoolean();
        RetryAfterWaits waits = RetryAfterWaits.onAnyThread(() -> !run.cancelled() && !stopped.get());
        return new TooManyRequestsStop(new RetryAfterHonouringHttp(http, CheckLog.labelled(eventLog, "Check access"),
                new ScanNotices()::firstTime, waits), stopped);
    }

    private void logEnd(AccessRunSummary summary, Optional<String> stopReason) {
        String progress = summary.checked() + " of " + summary.total() + " items";
        stopReason.ifPresentOrElse(
                reason -> eventLog.warn("Check access stopped: " + reason + " (" + progress + " checked)"),
                () -> eventLog.info("Check access " + (summary.cancelled() ? "cancelled" : "finished") + ": "
                        + progress + ", " + summary.issues() + " issues"));
    }

    private Optional<String> stopReasonAfter(AccessRow row, RowOutcome outcome, RepeatedFailure failures,
                                             TooManyRequestsStop guarded) {
        return guarded.reason().or(() -> repeatedFailure(row, outcome, failures));
    }

    private Optional<String> repeatedFailure(AccessRow row, RowOutcome outcome, RepeatedFailure failures) {
        Optional<String> repeated = failures.repeatedBy(outcome.transportFailure());
        if (repeated.isEmpty()) {
            outcome.transportFailure().ifPresent(reason -> warnFailed(row, reason));
        }
        return repeated;
    }

    private RowOutcome checkRow(AccessRow row, TooManyRequestsStop guarded, Http counted,
                                AccessComparisonRun comparisons) {
        if (!writeReplays.permits(row.item().key())) {
            eventLog.info("Check access skipped " + row.item().key().name() + ": write replay is no longer confirmed");
            return RowOutcome.checked(0);
        }
        HttpRequestResponse base;
        try {
            base = counted.sendRequest(requests.forOwner(row));
        } catch (AccessRunStopped stopped) {
            return RowOutcome.checked(0);
        } catch (RuntimeException failure) {
            return RowOutcome.unreachable(reasonOf(failure));
        }
        return guarded.reason().isPresent() ? RowOutcome.checked(0) : compare(row, base, comparisons);
    }

    private RowOutcome compare(AccessRow row, HttpRequestResponse base, AccessComparisonRun comparisons) {
        try {
            List<AuditIssue> issues = upstreamEvidence.translate(comparisons.compare(base));
            issues.forEach(issueSink);
            return RowOutcome.checked(issues.size());
        } catch (AccessRunStopped stopped) {
            return RowOutcome.checked(0);
        } catch (RuntimeException failure) {
            warnFailed(row, reasonOf(failure));
            return RowOutcome.checked(0);
        }
    }

    private void warnFailed(AccessRow row, String reason) {
        eventLog.warn("Check access failed for " + row.item().key().name() + ": " + reason);
    }

    private static String reasonOf(RuntimeException failure) {
        return Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName());
    }

    private static List<AccessRow> writeRows(List<AccessRow> rows) {
        return rows.stream().filter(row -> row.item().writes()).toList();
    }
}
