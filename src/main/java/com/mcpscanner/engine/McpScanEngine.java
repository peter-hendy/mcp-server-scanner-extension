package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import burp.api.montoya.sitemap.SiteMap;
import com.mcpscanner.http.RequestTally;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.mcpscanner.engine.EngineLog.count;

public final class McpScanEngine {

    private final Http http;
    private final ScanInstrumentation instrumentation;
    private final BaseResolver bases;
    private final InvocationPlanner planner;
    private final InvocationRunner runner;
    private final EngineExecutor executor;
    private final EngineLog log;
    private final SiteMapIssueSink issues;
    private final InvocationIssueTally tally = new InvocationIssueTally();
    private final ScanHistory history;
    private final List<RegisteredCheck> checks = new CopyOnWriteArrayList<>();
    private final Set<EngineScan> running = ConcurrentHashMap.newKeySet();

    public McpScanEngine(Http http, AuditInsertionPointProvider insertionPoints, SiteMap siteMap,
                         EngineWorkers workers, EngineEnvironment environment) {
        this.http = http;
        this.instrumentation = environment.instrumentation();
        this.log = new EngineLog(environment.eventLog());
        this.bases = new BaseResolver(log);
        this.planner = new InvocationPlanner(insertionPoints, environment.requestNames());
        this.issues = new SiteMapIssueSink(siteMap);
        this.runner = new InvocationRunner(issues, tally, log);
        this.executor = new EngineExecutor(workers);
        this.history = new ScanHistory(environment.clock(), environment.watchedPayloads(), log);
    }

    public ScanHistory history() {
        return history;
    }

    public void addIssues(ActiveScanCheck check, List<AuditIssue> reported, Optional<ScanCredit> credit) {
        List<AuditIssue> added = issues.report(check::consolidateIssues, reported);
        if (tally.recorded(added)) {
            return;
        }
        credit.ifPresent(scanCredit -> history.find(scanCredit.scanId())
                .ifPresent(scan -> scan.credit(added, scanCredit.testedRequest())));
    }

    public int addIssuesConsolidatedBy(IssueConsolidation consolidation, List<AuditIssue> reported) {
        List<AuditIssue> added = issues.report(consolidation, reported);
        tally.recorded(added);
        return added.size();
    }

    public void register(ActiveScanCheck check, ScanCheckType type) {
        register(check, type, SkippedInvocations.UNREPORTED);
    }

    public void register(ActiveScanCheck check, ScanCheckType type, SkippedInvocations skips) {
        checks.add(new RegisteredCheck(check, type, skips));
    }

    public EngineScan scan(List<HttpRequestResponse> requests, ScanLabel label) {
        EngineScan scan = history.start(label);
        if (executor.isShutdown()) {
            return refused(scan);
        }
        executor.applyWorkerCount();
        List<RegisteredCheck> scanned = List.copyOf(checks);
        ScanRun run = new ScanRun(scan, instrumentation.instrument(http, scan.id(), scan::requestSent, scan::cancelled));
        running.add(scan);
        log.kept("started — " + count(requests.size(), "request") + ", " + count(scanned.size(), "check"));
        allOf(requests.stream().map(request -> scanBase(run, request, scanned)))
                .whenComplete((ignored, failure) -> finish(scan));
        return scan;
    }

    public void shutdown() {
        running.forEach(scan -> scan.cancel(SkipCause.EXTENSION_UNLOADED));
        executor.shutdown();
        running.forEach(this::finish);
    }

    private EngineScan refused(EngineScan scan) {
        log.warn("refused: the engine has shut down");
        scan.cancel();
        finish(scan);
        return scan;
    }

    private CompletableFuture<Void> scanBase(ScanRun scanRun, HttpRequestResponse request,
                                             List<RegisteredCheck> scanned) {
        RequestTally requests = RequestTally.started();
        ScanRun run = scanRun.countingInto(requests);
        return submit(run.scan(), () -> resolveUnlessCancelled(run, request), Optional::<HttpRequestResponse>empty)
                .thenCompose(base -> base.map(resolved -> runChecks(run, resolved, scanned))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)))
                .whenComplete((ignored, failure) -> log.debug(requests.summary("of " + request.request().url())))
                .exceptionally(failure -> logBaseFailure(request, failure));
    }

    private Optional<HttpRequestResponse> resolveUnlessCancelled(ScanRun run, HttpRequestResponse request) {
        return run.scan().cancelled() ? Optional.empty() : bases.resolve(run, request);
    }

    private CompletableFuture<Void> runChecks(ScanRun run, HttpRequestResponse base,
                                              List<RegisteredCheck> scanned) {
        EngineScan scan = run.scan();
        List<CheckInvocation> invocations = planner.plan(base, scanned);
        scan.plan(invocations.size());
        return allOf(invocations.stream()
                .map(invocation -> submit(scan, () -> invoke(run, invocation),
                        () -> skipped(scan, invocation))));
    }

    private List<AuditIssue> invoke(ScanRun run, CheckInvocation invocation) {
        EngineScan scan = run.scan();
        if (scan.cancelled()) {
            return skipped(scan, invocation);
        }
        List<AuditIssue> found = runner.run(run, invocation);
        scan.completed(found, invocation.requestName());
        return found;
    }

    private static List<AuditIssue> skipped(EngineScan scan, CheckInvocation invocation) {
        scan.skip(invocation.registered());
        return List.of();
    }

    private <T> CompletableFuture<T> submit(EngineScan scan, Supplier<T> work, Supplier<T> whenDropped) {
        ScanTask<T> task = new ScanTask<>(scan.pauseGate(), executor, work, whenDropped);
        task.schedule();
        return task.result();
    }

    private Void logBaseFailure(HttpRequestResponse request, Throwable failure) {
        log.error("could not scan " + request.request().url() + ": " + FailureDescription.of(failure), failure);
        return null;
    }

    private void finish(EngineScan scan) {
        running.remove(scan);
        if (scan.finish()) {
            history.finished();
            logFinish(scan.completion().join());
        }
    }

    private void logFinish(ScanProgress progress) {
        log.kept((progress.cancelled() ? "cancelled" : "finished") + " — " + progress.completed() + " of "
                + count(progress.planned(), "invocation") + " run, " + progress.skipped() + " skipped, "
                + count(progress.issuesReported(), "issue") + " reported");
        if (progress.watchedPayloads() > 0) {
            log.info(OutOfBandWatchText.of(progress.watchedPayloads()));
        }
    }

    private static CompletableFuture<Void> allOf(Stream<? extends CompletableFuture<?>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}
