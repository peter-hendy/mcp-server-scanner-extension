package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;

final class ScanModeText {

    private static final ScanModeText MCP_ONLY = new ScanModeText("Scan with extension (recommended)",
            "MCP checks only, run by the extension", "the extension's checks", "Extension", "extension checks only", "Target › Issues, progress in the Logger",
            "Target › Issues", "The extension's checks");
    private static final ScanModeText MCP_AND_BURP = new ScanModeText("Scan with Burp Scanner",
            "MCP checks plus Burp's built-in active checks, as a Burp audit task", "Burp Scanner (extension and built-in checks)", "Burp Scanner",
            "extension checks and Burp's built-in active checks", "Burp's Dashboard", "Burp's Dashboard",
            "Burp Scanner (the extension's checks and Burp's built-in active checks)");

    private final String menuLabel;
    private final String description;
    private final String label;
    private final String shortLabel;
    private final String checksRunSuffix;
    private final String resultsLocation;
    private final String issuesLocation;
    private final String invoker;

    private ScanModeText(String menuLabel, String description, String label, String shortLabel, String checksRunSuffix, String resultsLocation,
                         String issuesLocation, String invoker) {
        this.menuLabel = menuLabel;
        this.description = description;
        this.label = label;
        this.shortLabel = shortLabel;
        this.checksRunSuffix = checksRunSuffix;
        this.resultsLocation = resultsLocation;
        this.issuesLocation = issuesLocation;
        this.invoker = invoker;
    }

    static ScanModeText of(ScanMode mode) {
        return switch (mode) {
            case MCP_ONLY -> MCP_ONLY;
            case MCP_AND_BURP -> MCP_AND_BURP;
        };
    }

    String menuLabel() {
        return menuLabel;
    }

    String description() {
        return description;
    }

    String tooltip() {
        return "Scan mode: " + menuLabel + ": " + description + ". Click to change the scan mode.";
    }

    String label() {
        return label;
    }

    String shortLabel() {
        return shortLabel;
    }

    String checksRun(long mcpCheckCount) {
        return mcpCheckCount + " " + checksRunSuffix;
    }

    String resultsLocation() {
        return resultsLocation;
    }

    String issuesLocation() {
        return issuesLocation;
    }

    String invoker() {
        return invoker;
    }
}
