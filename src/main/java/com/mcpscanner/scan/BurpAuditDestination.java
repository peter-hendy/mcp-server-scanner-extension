package com.mcpscanner.scan;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.AuditConfiguration;
import burp.api.montoya.scanner.BuiltInAuditConfiguration;
import burp.api.montoya.scanner.Scanner;
import burp.api.montoya.scanner.audit.Audit;
import com.mcpscanner.logging.McpEventLog;

import java.util.List;
import java.util.Objects;

final class BurpAuditDestination implements ScanDestination {

    private final Scanner scanner;
    private final BurpTaskExecution taskExecution;
    private final McpEventLog eventLog;
    private final Runnable auditStarting;

    BurpAuditDestination(Scanner scanner, BurpTaskExecution taskExecution, McpEventLog eventLog,
                         Runnable auditStarting) {
        this.auditStarting = Objects.requireNonNull(auditStarting);
        this.scanner = Objects.requireNonNull(scanner);
        this.taskExecution = Objects.requireNonNull(taskExecution);
        this.eventLog = Objects.requireNonNull(eventLog);
    }

    @Override
    public String name() {
        return "Burp audit";
    }

    @Override
    public BurpAuditRun start(List<HttpRequest> requests) {
        auditStarting.run();
        Audit audit = scanner.startAudit(
                AuditConfiguration.auditConfiguration(BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS));
        eventLog.info("Audit started — " + requestCount(requests.size()));
        addAllOrDelete(audit, requests);
        warnIfPaused();
        return new BurpAuditRun(audit);
    }

    @Override
    public boolean waitsForPausedTasks() {
        return taskExecution.paused();
    }

    @Override
    public void resumePausedTasks() {
        taskExecution.resume();
    }

    private void warnIfPaused() {
        if (taskExecution.paused()) {
            eventLog.warn(BurpTaskExecution.PAUSED_WARNING);
        }
    }

    private static void addAllOrDelete(Audit audit, List<HttpRequest> requests) {
        try {
            requests.forEach(audit::addRequest);
        } catch (RuntimeException e) {
            audit.delete();
            throw e;
        }
    }

    private static String requestCount(int count) {
        return count == 1 ? "1 request" : count + " requests";
    }
}
