package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.RunningInvocation;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.engine.ScanLabel;
import com.mcpscanner.engine.ScanProgress;
import com.mcpscanner.engine.ScanSnapshot;

import com.mcpscanner.ui.widgets.SeverityLabels;
import com.mcpscanner.ui.widgets.SeverityOrder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

final class ScanText {

    static final String SEPARATOR = " · ";
    private final DateTimeFormatter clockTime;

    ScanText(ZoneId zone) {
        this.clockTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
    }

    String summary(ScanSnapshot snapshot) {
        return String.join(SEPARATOR, percent(snapshot.progress()) + "%", requests(snapshot.requests()),
                errors(snapshot.errors().total()), issues(snapshot.issues()));
    }

    String status(ScanSnapshot snapshot) {
        return switch (snapshot.status()) {
            case RUNNING -> "Running";
            case PAUSED -> "Paused";
            case FINISHED -> "Finished";
            case CANCELLED -> "Cancelled at " + percent(snapshot.progress()) + "%";
        };
    }

    String progress(ScanSnapshot snapshot) {
        ScanProgress progress = snapshot.progress();
        return grouped(progress.completed()) + " of " + grouped(progress.planned()) + " check runs";
    }

    String requests(int count) {
        return counted(count, "request");
    }

    String errors(int count) {
        return counted(count, "error");
    }

    String issues(ScanFindings findings) {
        String bySeverity = SeverityOrder.RATED.stream()
                .filter(severity -> findings.count(severity) > 0)
                .map(severity -> severityCount(findings.count(severity), severity))
                .collect(Collectors.joining(", "));
        String total = issueTotal(findings);
        return bySeverity.isEmpty() ? total : total + " (" + bySeverity + ")";
    }

    String issueTotal(ScanFindings findings) {
        return counted(findings.total(), "issue");
    }

    String severityCount(int count, AuditIssueSeverity severity) {
        return grouped(count) + " " + SeverityLabels.of(severity);
    }

    String nowRunning(RunningInvocation invocation) {
        String subject = invocation.check() + " — " + invocation.request();
        return invocation.field().map(field -> subject + SEPARATOR + field).orElse(subject);
    }

    String connection(ScanLabel label) {
        return String.join(SEPARATOR, label.host(), label.transport(), "as " + label.connectAs());
    }

    String times(ScanSnapshot snapshot) {
        String started = time(snapshot.startedAt());
        return snapshot.endedAt().map(ended -> started + " – " + time(ended)).orElse("Started " + started);
    }

    String time(Instant instant) {
        return clockTime.format(instant);
    }

    String watched(int payloads) {
        return counted(payloads, "Collaborator payload") + " still watched";
    }

    int percent(ScanProgress progress) {
        return progress.planned() == 0 ? 0 : progress.completed() * 100 / progress.planned();
    }

    private static String counted(int count, String noun) {
        return grouped(count) + " " + noun + (count == 1 ? "" : "s");
    }

    private static String grouped(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }
}
