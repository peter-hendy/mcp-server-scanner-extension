package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.ReportedIssue;

import java.util.Arrays;
import java.util.List;

public enum BurpAuditConfiguration {
    EXTENSIONS_ONLY("extensions-only"),
    WITH_BUILTINS("with-builtins");

    private final String id;

    BurpAuditConfiguration(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BurpAuditConfiguration parse(String id) {
        return Arrays.stream(values())
                .filter(configuration -> configuration.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("-PburpConfig must be one of "
                        + Arrays.stream(values()).map(BurpAuditConfiguration::id).toList() + ", got " + id));
    }

    public boolean divertsBuiltInChecks() {
        return this == EXTENSIONS_ONLY;
    }

    public List<ReportedIssue> builtInIssuesToRecord(List<ReportedIssue> builtInIssues) {
        return this == WITH_BUILTINS ? builtInIssues : List.of();
    }
}
