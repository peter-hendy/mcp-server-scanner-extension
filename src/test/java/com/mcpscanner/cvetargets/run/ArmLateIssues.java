package com.mcpscanner.cvetargets.run;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.LateIssueSettlement;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;
import com.mcpscanner.checks.api.SettlingLateIssues;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ArmLateIssues implements LateIssueSettlement {

    private record Collecting(CollaboratorWatch watch, SettlingLateIssues issues) {
    }

    private final AtomicReference<Collecting> current = new AtomicReference<>();

    public CheckServices collectingFor(CheckServices services) {
        CollaboratorWatch watch = services.collaboratorWatch();
        SettlingLateIssues issues = new SettlingLateIssues(watch);
        current.set(new Collecting(watch, issues));
        return services.reportingLateIssues(watch, issues);
    }

    @Override
    public List<AuditIssue> settle(McpCheck check) {
        return Optional.ofNullable(current.get()).map(collecting -> collecting.issues().settle(check))
                .orElse(List.of());
    }

    public void close() {
        Optional.ofNullable(current.get()).ifPresent(collecting -> collecting.watch().shutdown());
    }
}
